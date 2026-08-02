package com.devsecops.dashboard;

public class ScanNotFoundException extends RuntimeException {

    public ScanNotFoundException(String scanId) {
        super("Scan no encontrado: " + scanId);
    }
}
