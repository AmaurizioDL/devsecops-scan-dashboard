package com.devsecops.dashboard;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record ScanRunView(
        String scanId,
        String targetUrl,
        ScanPhase phase,
        int percent,
        Long etaSeconds,
        long elapsedSeconds,
        List<ScanFinding> findings,
        String errorMessage
) {
    public static ScanRunView from(ScanRun run) {
        ScanRun.Snapshot snapshot = run.snapshot();
        long elapsedSeconds = Duration.between(run.getStartedAt(), Instant.now()).toSeconds();
        return new ScanRunView(
                run.getScanId(),
                run.getTargetUrl(),
                snapshot.phase(),
                snapshot.percent(),
                snapshot.etaSeconds(),
                elapsedSeconds,
                snapshot.findings(),
                snapshot.errorMessage()
        );
    }
}
