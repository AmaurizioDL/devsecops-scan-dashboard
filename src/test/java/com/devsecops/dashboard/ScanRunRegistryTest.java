package com.devsecops.dashboard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanRunRegistryTest {

    @Test
    void begin_returnsNewRunWhenNoneInProgress() {
        ScanRunRegistry registry = new ScanRunRegistry(15);

        ScanRun run = registry.begin("http://a", Set.of());

        assertThat(run.getTargetUrl()).isEqualTo("http://a");
        assertThat(registry.find(run.getScanId())).contains(run);
    }

    @Test
    void begin_throwsScanAlreadyRunningExceptionWhenAScanIsActive() {
        ScanRunRegistry registry = new ScanRunRegistry(15);
        ScanRun first = registry.begin("http://a", Set.of()); // starts in SPIDER phase - active

        ScanAlreadyRunningException ex = assertThrows(ScanAlreadyRunningException.class,
                () -> registry.begin("http://b", Set.of()));

        assertThat(ex.getRunningScanId()).isEqualTo(first.getScanId());
    }

    @Test
    void begin_allowsNewRunOnceThePreviousOneReachedATerminalPhase() {
        ScanRunRegistry registry = new ScanRunRegistry(15);
        ScanRun first = registry.begin("http://a", Set.of());
        first.markDone(List.of());

        ScanRun second = registry.begin("http://b", Set.of());

        assertThat(registry.find(second.getScanId())).contains(second);
        // single-slot design: once replaced, the previous scan's status is no longer reachable
        assertThat(registry.find(first.getScanId())).isEmpty();
    }

    @Test
    void find_unknownScanId_returnsEmpty() {
        ScanRunRegistry registry = new ScanRunRegistry(15);

        assertThat(registry.find("does-not-exist")).isEmpty();
    }
}
