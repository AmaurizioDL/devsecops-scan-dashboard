package com.devsecops.dashboard;

public enum ScanPhase {
    SPIDER, ACTIVE_SCAN, DONE, STOPPED, FAILED;

    public boolean isActive() {
        return this == SPIDER || this == ACTIVE_SCAN;
    }
}
