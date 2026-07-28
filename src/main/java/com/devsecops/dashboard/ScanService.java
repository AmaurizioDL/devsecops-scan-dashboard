package com.devsecops.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ScanService {

    private final ZapClient zapClient;
    private final ScanFindingRepository scanFindingRepository;
    private final long pollIntervalMs;
    private final long maxWaitMs;

    public ScanService(ZapClient zapClient,
                        ScanFindingRepository scanFindingRepository,
                        @Value("${zap.scan.poll-interval-ms:2000}") long pollIntervalMs,
                        @Value("${zap.scan.max-wait-ms:600000}") long maxWaitMs) {
        this.zapClient = zapClient;
        this.scanFindingRepository = scanFindingRepository;
        this.pollIntervalMs = pollIntervalMs;
        this.maxWaitMs = maxWaitMs;
    }

    /**
     * Lanza spider + active scan contra targetUrl. Las alertas ya presentes justo
     * despues del spider se guardan de inmediato como PASSIVE (para no perderlas si
     * el active scan, que puede tardar mucho mas, excede el timeout); las que
     * aparecen recien despues del active scan se guardan como ACTIVE.
     * <p>
     * Si riskLevels no es null/vacio, el active scan se acota a solo los scanners
     * de esas categorias de riesgo (ver ZapScannerRiskCatalog), para reducir el
     * tiempo de scan cuando no se necesita cobertura completa.
     */
    public List<ScanFinding> runFullScan(String targetUrl, Set<ScanRiskLevel> riskLevels) {
        String spiderScanId = zapClient.startSpiderScan(targetUrl);
        awaitCompletion(() -> zapClient.getSpiderStatus(spiderScanId));

        List<ZapClient.ZapAlert> passiveAlerts = zapClient.getAlerts(targetUrl);
        Set<String> passiveAlertIds = passiveAlerts.stream()
                .map(ZapClient.ZapAlert::id)
                .collect(Collectors.toSet());
        List<ScanFinding> passiveFindings = scanFindingRepository.saveAll(
                passiveAlerts.stream()
                        .map(alert -> toScanFinding(targetUrl, alert, "PASSIVE", LocalDateTime.now()))
                        .toList());

        zapClient.configureScannersForRiskLevels(riskLevels);
        String activeScanId = zapClient.startActiveScan(targetUrl);
        awaitCompletion(() -> zapClient.getActiveScanStatus(activeScanId));

        List<ScanFinding> activeFindings = scanFindingRepository.saveAll(
                zapClient.getAlerts(targetUrl).stream()
                        .filter(alert -> !passiveAlertIds.contains(alert.id()))
                        .map(alert -> toScanFinding(targetUrl, alert, "ACTIVE", LocalDateTime.now()))
                        .toList());

        return Stream.concat(passiveFindings.stream(), activeFindings.stream()).toList();
    }

    private void awaitCompletion(IntSupplier statusSupplier) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        int status;
        do {
            status = statusSupplier.getAsInt();
            if (status >= 100) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("El scan de ZAP no termino dentro del tiempo maximo configurado (zap.scan.max-wait-ms)");
            }
            sleep(pollIntervalMs);
        } while (status < 100);
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
