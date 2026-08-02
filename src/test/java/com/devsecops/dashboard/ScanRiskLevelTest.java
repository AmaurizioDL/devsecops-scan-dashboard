package com.devsecops.dashboard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanRiskLevelTest {

    @Test
    void fromString_isCaseInsensitiveAndTrimsWhitespace() {
        assertThat(ScanRiskLevel.fromString("high")).isEqualTo(ScanRiskLevel.HIGH);
        assertThat(ScanRiskLevel.fromString(" Medium ")).isEqualTo(ScanRiskLevel.MEDIUM);
        assertThat(ScanRiskLevel.fromString("LOW")).isEqualTo(ScanRiskLevel.LOW);
    }

    @Test
    void fromString_invalidValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ScanRiskLevel.fromString("CRITICAL"));
    }
}
