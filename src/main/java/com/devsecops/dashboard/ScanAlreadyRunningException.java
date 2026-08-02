package com.devsecops.dashboard;

public class ScanAlreadyRunningException extends RuntimeException {

    private final String runningScanId;

    public ScanAlreadyRunningException(String runningScanId) {
        super("Ya hay un scan en progreso: " + runningScanId);
        this.runningScanId = runningScanId;
    }

    public String getRunningScanId() {
        return runningScanId;
    }
}
