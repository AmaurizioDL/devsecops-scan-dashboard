package com.devsecops.dashboard;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One scan's mutable state. {@code updateProgress}/{@code markDone}/{@code markStopped}/
 * {@code markFailed} are only ever called from the single worker thread executing this
 * scan's {@code ScanService} task; {@code snapshot()} is read from HTTP request threads.
 * Publication is done through a single {@link AtomicReference} so readers always see a
 * consistent, immutable view without needing to synchronize with the worker thread.
 */
public final class ScanRun {

    private final String scanId;
    private final String targetUrl;
    private final Set<ScanRiskLevel> riskLevels;
    private final Instant startedAt;
    private final int progressWindowSize;

    private final AtomicReference<Snapshot> snapshotRef;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    // Only ever touched by the worker thread executing this scan - no concurrent access.
    private final Deque<ProgressSample> samples = new ArrayDeque<>();
    private ScanPhase samplesPhase;

    public ScanRun(String targetUrl, Set<ScanRiskLevel> riskLevels, int progressWindowSize) {
        this.scanId = UUID.randomUUID().toString();
        this.targetUrl = targetUrl;
        this.riskLevels = riskLevels;
        this.progressWindowSize = progressWindowSize;
        this.startedAt = Instant.now();
        this.samplesPhase = ScanPhase.SPIDER;
        this.snapshotRef = new AtomicReference<>(new Snapshot(ScanPhase.SPIDER, 0, null, List.of(), null));
    }

    public String getScanId() {
        return scanId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public Set<ScanRiskLevel> getRiskLevels() {
        return riskLevels;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Snapshot snapshot() {
        return snapshotRef.get();
    }

    public boolean isActive() {
        return snapshot().phase().isActive();
    }

    public void requestStop() {
        stopRequested.set(true);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public void updateProgress(ScanPhase phase, int percent) {
        if (phase != samplesPhase) {
            samples.clear();
            samplesPhase = phase;
        }
        samples.addLast(new ProgressSample(Instant.now(), percent));
        while (samples.size() > progressWindowSize) {
            samples.removeFirst();
        }
        Long etaSeconds = ScanProgressEstimator.estimateEtaSeconds(List.copyOf(samples)).orElse(null);
        snapshotRef.set(new Snapshot(phase, percent, etaSeconds, List.of(), null));
    }

    public void markDone(List<ScanFinding> findings) {
        snapshotRef.set(new Snapshot(ScanPhase.DONE, 100, null, findings, null));
    }

    public void markStopped(List<ScanFinding> findings) {
        snapshotRef.set(new Snapshot(ScanPhase.STOPPED, snapshot().percent(), null, findings, null));
    }

    public void markFailed(String errorMessage) {
        snapshotRef.set(new Snapshot(ScanPhase.FAILED, snapshot().percent(), null, List.of(), errorMessage));
    }

    public record Snapshot(ScanPhase phase, int percent, Long etaSeconds, List<ScanFinding> findings, String errorMessage) {
    }
}
