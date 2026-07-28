package com.devsecops.dashboard;

public enum ScanRiskLevel {
    HIGH, MEDIUM, LOW;

    public static ScanRiskLevel fromString(String value) {
        return ScanRiskLevel.valueOf(value.trim().toUpperCase());
    }
}
