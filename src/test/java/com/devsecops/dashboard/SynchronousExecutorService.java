package com.devsecops.dashboard;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Test double: runs every submitted task immediately on the calling thread, so
 * ScanService.startScan(...) in tests behaves synchronously instead of needing
 * polling/sleeping to observe the outcome.
 */
final class SynchronousExecutorService extends AbstractExecutorService {

    private volatile boolean shutdown = false;

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
