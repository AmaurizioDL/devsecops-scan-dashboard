package com.devsecops.dashboard;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Estimates remaining scan time from a sliding window of recent (timestamp, percent)
 * samples: rate = delta% / delta seconds over the window, ETA = remaining% / rate.
 * <p>
 * Deliberately not a naive "elapsed * (100-%) / %" extrapolation from scan start (too
 * noisy in the first few percent) and not a historical-average-per-target model (needs
 * persisted scan history and has a cold-start problem for new targets) — both were
 * considered and dropped in favor of this sliding-window approach.
 */
public final class ScanProgressEstimator {

    private ScanProgressEstimator() {
    }

    public static Optional<Long> estimateEtaSeconds(List<ProgressSample> recentSamples) {
        if (recentSamples == null || recentSamples.size() < 2) {
            return Optional.empty();
        }

        ProgressSample first = recentSamples.get(0);
        ProgressSample last = recentSamples.get(recentSamples.size() - 1);

        double elapsedSeconds = Duration.between(first.timestamp(), last.timestamp()).toMillis() / 1000.0;
        int deltaPercent = last.percent() - first.percent();

        if (elapsedSeconds <= 0 || deltaPercent <= 0) {
            // stalled, or not enough elapsed time yet to trust a rate
            return Optional.empty();
        }

        double ratePerSecond = deltaPercent / elapsedSeconds;
        double remainingPercent = 100 - last.percent();
        return Optional.of(Math.round(remainingPercent / ratePerSecond));
    }
}
