package com.devsecops.dashboard;

import org.springframework.beans.factory.annotation.Value;
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
import java.util.stream.Stream;

@Service
public class ScanService {

    private final ZapClient zapClient;
    private final ScanFindingRepository scanFindingRepository;
    private final ScanRunRegistry scanRunRegistry;
    private final ExecutorService executorService;
    private final long pollIntervalMs;
    private final long maxWaitMs;

    public ScanService(ZapClient zapClient,
                        ScanFindingRepository scanFindingRepository,
                        ScanRunRegistry scanRunRegistry,
                        ExecutorService executorService,
                        @Value("${zap.scan.poll-interval-ms:2000}") long pollIntervalMs,
                        @Value("${zap.scan.max-wait-ms:600000}") long maxWaitMs) {
        this.zapClient = zapClient;
        this.scanFindingRepository = scanFindingRepository;
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
        executorService.submit(() -> executeScan(run));
        return run;
    }

    public Optional<ScanRun> findRun(String scanId) {
        return scanRunRegistry.find(scanId);
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

            List<ScanFinding> passiveFindings = saveNewFindings(
                    targetUrl, zapClient.getAlerts(targetUrl), "PASSIVE", knownKeys);

            if (stoppedDuringSpider) {
                run.markStopped(passiveFindings);
                return;
            }

            zapClient.configureScannersForRiskLevels(run.getRiskLevels());
            String activeScanId = zapClient.startActiveScan(targetUrl);
            boolean stoppedDuringActiveScan = awaitCompletion(
                    () -> zapClient.getActiveScanStatus(activeScanId), run, ScanPhase.ACTIVE_SCAN) == AwaitResult.STOPPED;
            if (stoppedDuringActiveScan) {
                zapClient.stopActiveScan(activeScanId);
            }

            List<ScanFinding> activeFindings = saveNewFindings(
                    targetUrl, zapClient.getAlerts(targetUrl), "ACTIVE", knownKeys);

            List<ScanFinding> allFindings = Stream.concat(passiveFindings.stream(), activeFindings.stream()).toList();

            if (stoppedDuringActiveScan) {
                run.markStopped(allFindings);
            } else {
                run.markDone(allFindings);
            }
        } catch (Exception e) {
            run.markFailed(e.getMessage());
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
    private List<ScanFinding> saveNewFindings(String targetUrl, List<ZapClient.ZapAlert> alerts,
                                              String scanType, Set<String> knownKeys) {
        List<ScanFinding> newFindings = new ArrayList<>();
        for (ZapClient.ZapAlert alert : alerts) {
            if (knownKeys.add(findingKey(alert.pluginId(), alert.alert(), alert.risk(), alert.url()))) {
                newFindings.add(toScanFinding(targetUrl, alert, scanType, LocalDateTime.now()));
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
