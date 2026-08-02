package com.devsecops.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private ZapClient zapClient;

    @Mock
    private ScanFindingRepository scanFindingRepository;

    private ScanRunRegistry scanRunRegistry;
    private ScanService scanService;

    @BeforeEach
    void setUp() {
        scanRunRegistry = new ScanRunRegistry(15);
        scanService = new ScanService(zapClient, scanFindingRepository, scanRunRegistry,
                new SynchronousExecutorService(), 1L, 5_000L);
        lenient().when(scanFindingRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startScan_savesPassiveFindingsThenOnlyNewAlertsAsActive_andMarksDone() {
        String targetUrl = "http://juice-shop:3000";
        when(zapClient.startSpiderScan(targetUrl)).thenReturn("spider-1");
        when(zapClient.getSpiderStatus("spider-1")).thenReturn(100);
        when(zapClient.startActiveScan(targetUrl)).thenReturn("active-1");
        when(zapClient.getActiveScanStatus("active-1")).thenReturn(100);

        ZapClient.ZapAlert seenDuringSpider = new ZapClient.ZapAlert(
                "1", "Directory Browsing", "Low", "0", targetUrl + "/assets", "d1", "s1");
        ZapClient.ZapAlert onlySeenAfterActiveScan = new ZapClient.ZapAlert(
                "2", "SQL Injection", "High", "89", targetUrl + "/login", "d2", "s2");

        when(zapClient.getAlerts(targetUrl))
                .thenReturn(List.of(seenDuringSpider))
                .thenReturn(List.of(seenDuringSpider, onlySeenAfterActiveScan));

        ScanRun run = scanService.startScan(targetUrl, Set.of());

        ScanRun.Snapshot snapshot = run.snapshot();
        assertThat(snapshot.phase()).isEqualTo(ScanPhase.DONE);
        assertThat(snapshot.percent()).isEqualTo(100);
        assertThat(snapshot.findings()).extracting(ScanFinding::getAlertName)
                .containsExactly("Directory Browsing", "SQL Injection");
        assertThat(snapshot.findings().get(0).getScanType()).isEqualTo("PASSIVE");
        assertThat(snapshot.findings().get(1).getScanType()).isEqualTo("ACTIVE");

        verify(scanFindingRepository, times(2)).saveAll(anyList());
    }

    @Test
    void startScan_parsesCweIdEdgeCases() {
        String targetUrl = "http://target";
        when(zapClient.startSpiderScan(targetUrl)).thenReturn("spider-1");
        when(zapClient.getSpiderStatus("spider-1")).thenReturn(100);
        when(zapClient.startActiveScan(targetUrl)).thenReturn("active-1");
        when(zapClient.getActiveScanStatus("active-1")).thenReturn(100);

        List<ZapClient.ZapAlert> alerts = List.of(
                new ZapClient.ZapAlert("1", "Null CWE", "Low", null, targetUrl, "d", "s"),
                new ZapClient.ZapAlert("2", "Negative CWE", "Low", "-1", targetUrl, "d", "s"),
                new ZapClient.ZapAlert("3", "Non-numeric CWE", "Low", "n/a", targetUrl, "d", "s"),
                new ZapClient.ZapAlert("4", "Valid CWE", "High", " 79 ", targetUrl, "d", "s")
        );
        when(zapClient.getAlerts(targetUrl)).thenReturn(alerts, alerts);

        ScanRun run = scanService.startScan(targetUrl, Set.of());
        List<ScanFinding> findings = run.snapshot().findings();

        assertThat(findByAlertName(findings, "Null CWE").getCweId()).isNull();
        assertThat(findByAlertName(findings, "Negative CWE").getCweId()).isNull();
        assertThat(findByAlertName(findings, "Non-numeric CWE").getCweId()).isNull();
        assertThat(findByAlertName(findings, "Valid CWE").getCweId()).isEqualTo(79);
    }

    @Test
    void startScan_passesRiskLevelsThroughToZapClient() {
        String targetUrl = "http://target";
        when(zapClient.startSpiderScan(targetUrl)).thenReturn("spider-1");
        when(zapClient.getSpiderStatus("spider-1")).thenReturn(100);
        when(zapClient.startActiveScan(targetUrl)).thenReturn("active-1");
        when(zapClient.getActiveScanStatus("active-1")).thenReturn(100);
        when(zapClient.getAlerts(targetUrl)).thenReturn(List.of());

        Set<ScanRiskLevel> riskLevels = Set.of(ScanRiskLevel.HIGH, ScanRiskLevel.LOW);
        scanService.startScan(targetUrl, riskLevels);

        verify(zapClient).configureScannersForRiskLevels(riskLevels);
    }

    @Test
    void startScan_zapScanNeverCompletesWithinMaxWait_marksRunFailed() {
        // negative max-wait makes the deadline already-past on the very first poll,
        // so this fails fast instead of actually sleeping/looping in the test.
        scanService = new ScanService(zapClient, scanFindingRepository, scanRunRegistry,
                new SynchronousExecutorService(), 1L, -1_000L);
        when(zapClient.startSpiderScan("http://target")).thenReturn("spider-1");
        when(zapClient.getSpiderStatus("spider-1")).thenReturn(42);

        ScanRun run = scanService.startScan("http://target", Set.of());

        assertThat(run.snapshot().phase()).isEqualTo(ScanPhase.FAILED);
        assertThat(run.snapshot().errorMessage()).contains("max-wait-ms");
    }

    @Test
    void executeScan_stopRequestedDuringSpiderPhase_savesOnlyPassiveAndSkipsActiveScan() {
        String targetUrl = "http://target";
        when(zapClient.startSpiderScan(targetUrl)).thenReturn("spider-1");
        when(zapClient.getSpiderStatus("spider-1")).thenReturn(50); // never reaches 100
        when(zapClient.getAlerts(targetUrl)).thenReturn(List.of(
                new ZapClient.ZapAlert("1", "Directory Browsing", "Low", "0", targetUrl, "d", "s")));

        ScanRun run = scanRunRegistry.begin(targetUrl, Set.of());
        run.requestStop();

        scanService.executeScan(run);

        assertThat(run.snapshot().phase()).isEqualTo(ScanPhase.STOPPED);
        assertThat(run.snapshot().findings()).hasSize(1);
        assertThat(run.snapshot().findings().get(0).getScanType()).isEqualTo("PASSIVE");
        verify(zapClient).stopSpiderScan("spider-1");
        verify(zapClient, never()).startActiveScan(anyString());
    }

    @Test
    void executeScan_stopRequestedDuringActiveScanPhase_savesPassiveAndPartialActiveFindings() {
        String targetUrl = "http://target";
        when(zapClient.startSpiderScan(targetUrl)).thenReturn("spider-1");
        when(zapClient.getSpiderStatus("spider-1")).thenReturn(100);
        when(zapClient.startActiveScan(targetUrl)).thenReturn("active-1");

        ZapClient.ZapAlert passiveAlert = new ZapClient.ZapAlert("1", "Directory Browsing", "Low", "0", targetUrl, "d", "s");
        ZapClient.ZapAlert activeAlert = new ZapClient.ZapAlert("2", "SQL Injection", "High", "89", targetUrl, "d2", "s2");
        when(zapClient.getAlerts(targetUrl))
                .thenReturn(List.of(passiveAlert))
                .thenReturn(List.of(passiveAlert, activeAlert));

        ScanRun run = scanRunRegistry.begin(targetUrl, Set.of());
        // Flip the stop flag as a side effect of the first active-scan poll, simulating
        // a stop request that arrives once the active scan phase is under way.
        when(zapClient.getActiveScanStatus("active-1")).thenAnswer(invocation -> {
            run.requestStop();
            return 30;
        });

        scanService.executeScan(run);

        assertThat(run.snapshot().phase()).isEqualTo(ScanPhase.STOPPED);
        assertThat(run.snapshot().findings()).hasSize(2);
        assertThat(run.snapshot().findings().get(1).getScanType()).isEqualTo("ACTIVE");
        verify(zapClient).stopActiveScan("active-1");
    }

    private static ScanFinding findByAlertName(List<ScanFinding> findings, String alertName) {
        return findings.stream()
                .filter(f -> f.getAlertName().equals(alertName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finding with alertName=" + alertName));
    }
}
