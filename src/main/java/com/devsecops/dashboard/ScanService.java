package com.devsecops.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

            List<ZapClient.ZapAlert> passiveAlerts = zapClient.getAlerts(targetUrl);
            Set<String> passiveAlertIds = passiveAlerts.stream()
                    .map(ZapClient.ZapAlert::id)
                    .collect(Collectors.toSet());
            List<ScanFinding> passiveFindings = scanFindingRepository.saveAll(
                    passiveAlerts.stream()
                            .map(alert -> toScanFinding(targetUrl, alert, "PASSIVE", LocalDateTime.now()))
                            .toList());

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

            List<ScanFinding> activeFindings = scanFindingRepository.saveAll(
                    zapClient.getAlerts(targetUrl).stream()
                            .filter(alert -> !passiveAlertIds.contains(alert.id()))
                            .map(alert -> toScanFinding(targetUrl, alert, "ACTIVE", LocalDateTime.now()))
                            .toList());

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

    private ScanFinding toScanFinding(String targetUrl, ZapClient.ZapAlert alert, String scanType, LocalDateTime detectedAt) {
        ScanFinding finding = new ScanFinding(
                targetUrl,
                scanType,
                alert.alert(),
                alert.risk(),
                parseCweId(alert.cweid()),
                alert.url(),
                alert.description(),
                alert.solution()
        );
        finding.setDetectedAt(detectedAt);
        return finding;
    }

    private Integer parseCweId(String cweid) {
        if (cweid == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(cweid.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
