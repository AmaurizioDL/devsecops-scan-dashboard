package com.devsecops.dashboard;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanProgressEstimatorTest {

    @Test
    void estimateEtaSeconds_noSamples_returnsEmpty() {
        assertThat(ScanProgressEstimator.estimateEtaSeconds(null)).isEmpty();
        assertThat(ScanProgressEstimator.estimateEtaSeconds(List.of())).isEmpty();
    }

    @Test
    void estimateEtaSeconds_singleSample_returnsEmpty() {
        List<ProgressSample> samples = List.of(new ProgressSample(Instant.now(), 10));

        assertThat(ScanProgressEstimator.estimateEtaSeconds(samples)).isEmpty();
    }

    @Test
    void estimateEtaSeconds_stalledProgress_returnsEmpty() {
        Instant t0 = Instant.now();
        List<ProgressSample> samples = List.of(
                new ProgressSample(t0, 30),
                new ProgressSample(t0.plusSeconds(10), 30)); // no movement in 10s

        assertThat(ScanProgressEstimator.estimateEtaSeconds(samples)).isEmpty();
    }

    @Test
    void estimateEtaSeconds_zeroElapsedTime_returnsEmpty() {
        Instant t0 = Instant.now();
        List<ProgressSample> samples = List.of(
                new ProgressSample(t0, 10),
                new ProgressSample(t0, 20)); // same timestamp

        assertThat(ScanProgressEstimator.estimateEtaSeconds(samples)).isEmpty();
    }

    @Test
    void estimateEtaSeconds_steadyRate_computesRemainingTimeFromRecentWindow() {
        Instant t0 = Instant.now();
        // 20% -> 40% over 10s = 2%/s; 60% remaining / 2%/s = 30s
        List<ProgressSample> samples = List.of(
                new ProgressSample(t0, 20),
                new ProgressSample(t0.plusSeconds(5), 30),
                new ProgressSample(t0.plusSeconds(10), 40));

        assertThat(ScanProgressEstimator.estimateEtaSeconds(samples)).contains(30L);
    }
}
