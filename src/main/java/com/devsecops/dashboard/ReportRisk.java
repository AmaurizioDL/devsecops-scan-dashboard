package com.devsecops.dashboard;

/**
 * Reglas de riesgo compartidas por ambos reportes. El riesgo y la confianza vienen
 * tal cual de ZAP (no se recalculan con CVSS ni OWASP Risk Rating: decision explicita
 * de alcance); aca solo se normalizan, ordenan y traducen a prioridad/plazo.
 */
final class ReportRisk {

    static final String HIGH = "High";
    static final String MEDIUM = "Medium";
    static final String LOW = "Low";
    static final String INFORMATIONAL = "Informational";

    static final String UNKNOWN_CONFIDENCE = "Unknown";

    private ReportRisk() {
    }

    static String normalizeRisk(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return INFORMATIONAL;
        }
        return capitalize(riskLevel);
    }

    /** 0 = mas grave. */
    static int riskRank(String riskLevel) {
        return switch (normalizeRisk(riskLevel)) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
            default -> 3;
        };
    }

    static String priorityFor(String riskLevel) {
        return switch (normalizeRisk(riskLevel)) {
            case HIGH -> "P1";
            case MEDIUM -> "P2";
            case LOW -> "P3";
            default -> "P4";
        };
    }

    static String timeframeFor(String riskLevel) {
        return switch (normalizeRisk(riskLevel)) {
            case HIGH -> "Immediate (0-7 days)";
            case MEDIUM -> "Within 30 days";
            case LOW -> "Within 90 days";
            default -> "Backlog / next review";
        };
    }

    /** Hallazgos sin confianza registrada (filas anteriores a esta columna) quedan como Unknown. */
    static String normalizeConfidence(String confidence) {
        if (confidence == null || confidence.isBlank()) {
            return UNKNOWN_CONFIDENCE;
        }
        return capitalize(confidence);
    }

    /** 0 = mas confiable. ZAP usa Confirmed/High/Medium/Low/False Positive. */
    static int confidenceRank(String confidence) {
        return switch (normalizeConfidence(confidence)) {
            case "Confirmed" -> 0;
            case "High" -> 1;
            case "Medium" -> 2;
            case "Low" -> 3;
            case UNKNOWN_CONFIDENCE -> 4;
            default -> 5; // False positive
        };
    }

    private static String capitalize(String value) {
        String trimmed = value.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1).toLowerCase();
    }
}
