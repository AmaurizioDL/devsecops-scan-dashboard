package com.devsecops.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the single currently-running (or last-finished) scan. This project only ever
 * runs one scan at a time, matching how ZAP itself and the previous blocking design
 * already behaved - so a single slot is enough, no job queue needed. A finished scan's
 * status is only reachable via {@link #find} until the next scan starts and replaces it.
 */
@Component
public class ScanRunRegistry {

    private final int progressWindowSize;
    private final AtomicReference<ScanRun> current = new AtomicReference<>();

    public ScanRunRegistry(@Value("${zap.scan.progress-window-size:15}") int progressWindowSize) {
        this.progressWindowSize = progressWindowSize;
    }

    public ScanRun begin(String targetUrl, Set<ScanRiskLevel> riskLevels) {
        while (true) {
            ScanRun existing = current.get();
            if (existing != null && existing.isActive()) {
                throw new ScanAlreadyRunningException(existing.getScanId());
            }
            ScanRun newRun = new ScanRun(targetUrl, riskLevels, progressWindowSize);
            if (current.compareAndSet(existing, newRun)) {
                return newRun;
            }
        }
    }

    public Optional<ScanRun> find(String scanId) {
        ScanRun run = current.get();
        if (run != null && run.getScanId().equals(scanId)) {
            return Optional.of(run);
        }
        return Optional.empty();
    }
}
