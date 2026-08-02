package com.devsecops.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private ZapClient zapClient;

    @Mock
    private ScanFindingRepository scanFindingRepository;

    private ScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new ScanService(zapClient, scanFindingRepository, 1L, 5_000L);
        lenient().when(scanFindingRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void runFullScan_savesPassiveFindingsThenOnlyNewAlertsAsActive() {
        String targetUrl = "http://juice-shop:3000";
        when(zapClient.startSpiderScan(targetUrl)).thenReturn("spider-1");
        when(zapClient.getSpiderStatus("spider-1")).thenReturn(100);
        when(zapClient.startActiveScan(targetUrl)).thenReturn("active-1");
        when(zapClient.getActiveScanStatus("active-1")).thenReturn(100);

        ZapClient.ZapAlert seenDuringSpider = new ZapClient.ZapAlert(
                "1", "Directory Browsing", "Low", "0", targetUrl + "/assets", "d1", "s1");
        ZapClient.ZapAlert onlySeenAfterActiveScan = new ZapClient.ZapAlert(
                "2", "SQL Injection", "High", "89", targetUrl + "/login", "d2", "s2");

        // First call happens right after the spider, second after the active scan.
        when(zapClient.getAlerts(targetUrl))
                .thenReturn(List.of(seenDuringSpider))
                .thenReturn(List.of(seenDuringSpider, onlySeenAfterActiveScan));

        List<ScanFinding> result = scanService.runFullScan(targetUrl, Set.of());

        assertThat(result).hasSize(2);

        ScanFinding passive = result.get(0);
        assertThat(passive.getScanType()).isEqualTo("PASSIVE");
        assertThat(passive.getAlertName()).isEqualTo("Directory Browsing");

        ScanFinding active = result.get(1);
        assertThat(active.getScanType()).isEqualTo("ACTIVE");
        assertThat(active.getAlertName()).isEqualTo("SQL Injection");

        // the alert already saved as PASSIVE must not be saved again as ACTIVE
        assertThat(result).extracting(ScanFinding::getAlertName)
                .containsExactly("Directory Browsing", "SQL Injection");

        verify(scanFindingRepository, org.mockito.Mockito.times(2)).saveAll(anyList());
    }

    @Test
    void runFullScan_parsesCweIdEdgeCases() {
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
        // same alerts before and after the active scan -> nothing new to save as ACTIVE
        when(zapClient.getAlerts(targetUrl)).thenReturn(alerts, alerts);

        List<ScanFinding> result = scanService.runFullScan(targetUrl, Set.of());

        assertThat(findByAlertName(result, "Null CWE").getCweId()).isNull();
        assertThat(findByAlertName(result, "Negative CWE").getCweId()).isNull();
        assertThat(findByAlertName(result, "Non-numeric CWE").getCweId()).isNull();
        assertThat(findByAlertName(result, "Valid CWE").getCweId()).isEqualTo(79);
    }

    @Test
    void runFullScan_passesRiskLevelsThroughToZapClient() {
        String targetUrl = "http://target";
        when(zapClient.startSpiderScan(targetUrl)).thenReturn("spider-1");
        when(zapClient.getSpiderStatus("spider-1")).thenReturn(100);
        when(zapClient.startActiveScan(targetUrl)).thenReturn("active-1");
        when(zapClient.getActiveScanStatus("active-1")).thenReturn(100);
        when(zapClient.getAlerts(targetUrl)).thenReturn(List.of());

        Set<ScanRiskLevel> riskLevels = Set.of(ScanRiskLevel.HIGH, ScanRiskLevel.LOW);
        scanService.runFullScan(targetUrl, riskLevels);

        verify(zapClient).configureScannersForRiskLevels(riskLevels);
    }

    @Test
    void runFullScan_throwsWhenZapScanNeverCompletesWithinMaxWait() {
        // negative max-wait makes the deadline already-past on the very first poll,
        // so this fails fast instead of actually sleeping/looping in the test.
        scanService = new ScanService(zapClient, scanFindingRepository, 1L, -1_000L);
        when(zapClient.startSpiderScan("http://target")).thenReturn("spider-1");
        when(zapClient.getSpiderStatus("spider-1")).thenReturn(42);

        assertThrows(IllegalStateException.class,
                () -> scanService.runFullScan("http://target", Set.of()));
    }

    private static ScanFinding findByAlertName(List<ScanFinding> findings, String alertName) {
        return findings.stream()
                .filter(f -> f.getAlertName().equals(alertName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finding with alertName=" + alertName));
    }
}
