package com.devsecops.dashboard;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ZapScannerRiskCatalogTest {

    @Test
    void scannerIdsForRiskLevels_high_includesKnownCriticalScannersOnly() {
        Set<String> ids = ZapScannerRiskCatalog.scannerIdsForRiskLevels(Set.of(ScanRiskLevel.HIGH));

        assertThat(ids).contains("40018"); // SQL Injection
        assertThat(ids).contains("90020"); // Remote OS Command Injection
        assertThat(ids).doesNotContain("0");       // Directory Browsing (LOW)
        assertThat(ids).doesNotContain("40042");   // Spring Actuator Info Leak (MEDIUM)
    }

    @Test
    void scannerIdsForRiskLevels_emptySet_returnsNoScanners() {
        Set<String> ids = ZapScannerRiskCatalog.scannerIdsForRiskLevels(Set.of());

        assertThat(ids).isEmpty();
    }

    @Test
    void scannerIdsForRiskLevels_perLevelResultsArePartitionedAndAddUpToTheFullCatalog() {
        Set<String> high = ZapScannerRiskCatalog.scannerIdsForRiskLevels(Set.of(ScanRiskLevel.HIGH));
        Set<String> medium = ZapScannerRiskCatalog.scannerIdsForRiskLevels(Set.of(ScanRiskLevel.MEDIUM));
        Set<String> low = ZapScannerRiskCatalog.scannerIdsForRiskLevels(Set.of(ScanRiskLevel.LOW));
        Set<String> all = ZapScannerRiskCatalog.scannerIdsForRiskLevels(
                Set.of(ScanRiskLevel.HIGH, ScanRiskLevel.MEDIUM, ScanRiskLevel.LOW));

        assertThat(high).isNotEmpty();
        assertThat(medium).isNotEmpty();
        assertThat(low).isNotEmpty();

        assertThat(high).doesNotContainAnyElementsOf(medium);
        assertThat(high).doesNotContainAnyElementsOf(low);
        assertThat(medium).doesNotContainAnyElementsOf(low);

        Set<String> union = new HashSet<>(high);
        union.addAll(medium);
        union.addAll(low);
        assertThat(all).containsExactlyInAnyOrderElementsOf(union);
    }
}
