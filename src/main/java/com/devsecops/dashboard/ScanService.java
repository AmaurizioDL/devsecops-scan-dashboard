package com.devsecops.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

@Service
public class ScanService {

    private final ZapClient zapClient;
    private final ScanFindingRepository scanFindingRepository;
    private final ScanRecordRepository scanRecordRepository;
    private final ScanRunRegistry scanRunRegistry;
    private final ExecutorService executorService;
    private final long pollIntervalMs;
    private final long maxWaitMs;

    public ScanService(ZapClient zapClient,
                        ScanFindingRepository scanFindingRepository,
                        ScanRecordRepository scanRecordRepository,
                        ScanRunRegistry scanRunRegistry,
                        ExecutorService executorService,
                        @Value("${zap.scan.poll-interval-ms:2000}") long pollIntervalMs,
                        @Value("${zap.scan.max-wait-ms:600000}") long maxWaitMs) {
        this.zapClient = zapClient;
        this.scanFindingRepository = scanFindingRepository;
        this.scanRecordRepository = scanRecordRepository;
        this.scanRunRegistry = scanRunRegistry;
        this.executorService = executorService;
        this.pollIntervalMs = pollIntervalMs;
        this.maxWaitMs = maxWaitMs;
    }

    /**
     * Starts a spider + active scan against targetUrl in the background and returns
     * immediately. Throws ScanAlreadyRunningException if a scan is already in progress
     * (this project only ever runs one scan at a time).
     */
    public ScanRun startScan(String targetUrl, Set<ScanRiskLevel> riskLevels) {
        ScanRun run = scanRunRegistry.begin(targetUrl, riskLevels);
        try {
            scanRecordRepository.save(ScanRecord.startedFrom(run));
        } catch (RuntimeException e) {
            // Free the single scan slot: this run will never be executed.
            run.markFailed(e.getMessage());
            throw e;
        }
        executorService.submit(() -> executeScan(run));
        return run;
    }

    public Optional<ScanRun> findRun(String scanId) {
        return scanRunRegistry.find(scanId);
    }

    /** The scan running right now, if any (lets the UI resume following it after a reload). */
    public Optional<ScanRun> findActiveRun() {
        return scanRunRegistry.current().filter(ScanRun::isActive);
    }

    /**
     * A scan only runs inside this process, so on startup any history row still RUNNING
     * belongs to a backend that died mid-scan and is closed as FAILED.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void closeInterruptedScans() {
        List<ScanRecord> interrupted = scanRecordRepository.findAllByStatus(ScanRecord.RUNNING);
        LocalDateTime now = LocalDateTime.now();
        interrupted.forEach(record -> record.markInterrupted(now));
        if (!interrupted.isEmpty()) {
            scanRecordRepository.saveAll(interrupted);
        }
    }

    public void requestStop(String scanId) {
        scanRunRegistry.find(scanId)
                .orElseThrow(() -> new ScanNotFoundException(scanId))
                .requestStop();
    }

    /**
     * Runs on the background executor. Las alertas ya presentes justo despues del spider
     * se guardan de inmediato como PASSIVE (para no perderlas si el active scan, que puede
     * tardar mucho mas, excede el timeout o se detiene); las que aparecen recien despues
     * del active scan se guardan como ACTIVE.
     * <p>
     * Solo se guardan hallazgos nuevos para el target (ver findingKey): ZAP conserva en su
     * sesion las alertas de scans anteriores y un re-scan vuelve a encontrar lo mismo, asi que
     * sin este filtro cada scan duplicaba los hallazgos ya guardados.
     * <p>
     * Si riskLevels no es null/vacio, el active scan se acota a solo los scanners de esas
     * categorias de riesgo (ver ZapScannerRiskCatalog), para reducir el tiempo de scan
     * cuando no se necesita cobertura completa.
     */
    // Package-private (not private) so tests can drive it directly with a pre-built,
    // already-flagged-for-stop ScanRun without needing real thread concurrency.
    void executeScan(ScanRun run) {
        // Every finding saved so far, so a scan that fails half way still records them.
        List<ScanFinding> savedFindings = new ArrayList<>();
        try {
            String targetUrl = run.getTargetUrl();

            String spiderScanId = zapClient.startSpiderScan(targetUrl);
            boolean stoppedDuringSpider = awaitCompletion(
                    () -> zapClient.getSpiderStatus(spiderScanId), run, ScanPhase.SPIDER) == AwaitResult.STOPPED;
            if (stoppedDuringSpider) {
                zapClient.stopSpiderScan(spiderScanId);
            }

            Set<String> knownKeys = scanFindingRepository.findAllByTargetUrlOrderByDetectedAtDesc(targetUrl).stream()
                    .map(f -> findingKey(f.getPluginId(), f.getAlertName(), f.getRiskLevel(), f.getAffectedUrl()))
                    .collect(Collectors.toCollection(HashSet::new));

            savedFindings.addAll(saveNewFindings(run, zapClient.getAlerts(targetUrl), "PASSIVE", knownKeys));

            if (stoppedDuringSpider) {
                finish(run, ScanPhase.STOPPED, savedFindings, null);
                return;
            }

            zapClient.configureScannersForRiskLevels(run.getRiskLevels());
            String activeScanId = zapClient.startActiveScan(targetUrl);
            boolean stoppedDuringActiveScan = awaitCompletion(
                    () -> zapClient.getActiveScanStatus(activeScanId), run, ScanPhase.ACTIVE_SCAN) == AwaitResult.STOPPED;
            if (stoppedDuringActiveScan) {
                zapClient.stopActiveScan(activeScanId);
            }

            savedFindings.addAll(saveNewFindings(run, zapClient.getAlerts(targetUrl), "ACTIVE", knownKeys));

            finish(run, stoppedDuringActiveScan ? ScanPhase.STOPPED : ScanPhase.DONE, savedFindings, null);
        } catch (Exception e) {
            finish(run, ScanPhase.FAILED, savedFindings, e.getMessage());
        }
    }

    /**
     * Closes the scan's history row, then publishes the final state to pollers, so the UI
     * never sees a finished scan still listed as RUNNING. The in-memory run is marked even
     * if saving the row fails, otherwise the single scan slot would stay taken forever.
     */
    private void finish(ScanRun run, ScanPhase phase, List<ScanFinding> savedFindings, String errorMessage) {
        List<ScanFinding> findings = List.copyOf(savedFindings);
        try {
            ScanRecord record = scanRecordRepository.findById(run.getScanId())
                    .orElseGet(() -> ScanRecord.startedFrom(run));
            record.finish(phase, findings, errorMessage, LocalDateTime.now());
            scanRecordRepository.save(record);
        } finally {
            switch (phase) {
                case DONE -> run.markDone(findings);
                case STOPPED -> run.markStopped(findings);
                default -> run.markFailed(errorMessage);
            }
        }
    }

    private enum AwaitResult {
        COMPLETED, STOPPED
    }

    private AwaitResult awaitCompletion(IntSupplier statusSupplier, ScanRun run, ScanPhase phase) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (true) {
            int status = statusSupplier.getAsInt();
            run.updateProgress(phase, status);
            if (status >= 100) {
                return AwaitResult.COMPLETED;
            }
            if (run.isStopRequested()) {
                return AwaitResult.STOPPED;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("El scan de ZAP no termino dentro del tiempo maximo configurado (zap.scan.max-wait-ms)");
            }
            sleep(pollIntervalMs);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido mientras se esperaba a que ZAP terminara el scan", e);
        }
    }

    /**
     * Saves the alerts whose finding key is not in knownKeys (and adds their keys to it), so
     * an alert already stored, or repeated within the same batch, is never saved twice.
     */
    private List<ScanFinding> saveNewFindings(ScanRun run, List<ZapClient.ZapAlert> alerts,
                                              String scanType, Set<String> knownKeys) {
        List<ScanFinding> newFindings = new ArrayList<>();
        for (ZapClient.ZapAlert alert : alerts) {
            if (knownKeys.add(findingKey(alert.pluginId(), alert.alert(), alert.risk(), alert.url()))) {
                ScanFinding finding = toScanFinding(run.getTargetUrl(), alert, scanType, LocalDateTime.now());
                finding.setScanRunId(run.getScanId());
                newFindings.add(finding);
            }
        }
        return newFindings.isEmpty() ? List.of() : scanFindingRepository.saveAll(newFindings);
    }

    /**
     * One finding per ZAP rule, risk and affected URL within a target (the rule is identified
     * by pluginId, or by alert name for rows stored before pluginId existed), the same rule
     * key the reports use to consolidate vulnerabilities.
     */
    static String findingKey(String pluginId, String alertName, String riskLevel, String affectedUrl) {
        String rule = pluginId != null && !pluginId.isBlank() ? pluginId : alertName;
        return rule + '|' + riskLevel + '|' + affectedUrl;
    }

    private ScanFinding toScanFinding(String targetUrl, ZapClient.ZapAlert alert, String scanType, LocalDateTime detectedAt) {
        Integer cweId = parseCweId(alert.cweid());
        ScanFinding finding = new ScanFinding(
                targetUrl,
                scanType,
                alert.alert(),
                alert.risk(),
                cweId,
                OwaspTop10Mapper.map(alert.tags(), cweId).code(),
                alert.url(),
                alert.description(),
                alert.solution()
        );
        finding.setConfidence(alert.confidence());
        finding.setPluginId(alert.pluginId());
        finding.setEvidence(alert.evidence());
        finding.setReference(alert.reference());
        finding.setDetectedAt(detectedAt);
        return finding;
    }

    private Integer parseCweId(String cweid) {
        if (cweid == null) {
            return null;
        }
        try {
            // ZAP usa "0" (y a veces "-1") para alertas sin CWE asignado
            int value = Integer.parseInt(cweid.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
