package com.devsecops.dashboard;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ZAP no expone el riesgo como atributo estatico de cada scanner (solo se conoce
 * el riesgo real de una alerta despues de que el scanner la dispara). Este catalogo
 * clasifica los scanners de active scan de esta instancia de ZAP (Default Policy)
 * en HIGH/MEDIUM/LOW segun el impacto tipico de lo que detectan, para poder acotar
 * el active scan a solo las categorias de riesgo que el usuario quiera cubrir.
 */
public final class ZapScannerRiskCatalog {

    private static final Map<String, ScanRiskLevel> SCANNER_RISK_BY_ID = Map.ofEntries(
            // HIGH: RCE, inyeccion con impacto directo sobre el backend, fuga de secretos criticos
            Map.entry("6", ScanRiskLevel.HIGH),      // Path Traversal
            Map.entry("7", ScanRiskLevel.HIGH),      // Remote File Inclusion
            Map.entry("10048", ScanRiskLevel.HIGH),  // RCE - Shell Shock
            Map.entry("20015", ScanRiskLevel.HIGH),  // Heartbleed OpenSSL Vulnerability
            Map.entry("20018", ScanRiskLevel.HIGH),  // Remote Code Execution - CVE-2012-1823
            Map.entry("40009", ScanRiskLevel.HIGH),  // Server Side Include
            Map.entry("40014", ScanRiskLevel.HIGH),  // Cross Site Scripting (Persistent)
            Map.entry("40016", ScanRiskLevel.HIGH),  // Cross Site Scripting (Persistent) - Prime
            Map.entry("40017", ScanRiskLevel.HIGH),  // Cross Site Scripting (Persistent) - Spider
            Map.entry("40018", ScanRiskLevel.HIGH),  // SQL Injection
            Map.entry("40019", ScanRiskLevel.HIGH),  // SQL Injection - MySQL (Time Based)
            Map.entry("40020", ScanRiskLevel.HIGH),  // SQL Injection - Hypersonic SQL (Time Based)
            Map.entry("40021", ScanRiskLevel.HIGH),  // SQL Injection - Oracle (Time Based)
            Map.entry("40022", ScanRiskLevel.HIGH),  // SQL Injection - PostgreSQL (Time Based)
            Map.entry("40026", ScanRiskLevel.HIGH),  // Cross Site Scripting (DOM Based)
            Map.entry("40027", ScanRiskLevel.HIGH),  // SQL Injection - MsSQL (Time Based)
            Map.entry("40034", ScanRiskLevel.HIGH),  // .env Information Leak (credenciales/secretos)
            Map.entry("40043", ScanRiskLevel.HIGH),  // Log4Shell
            Map.entry("40045", ScanRiskLevel.HIGH),  // Spring4Shell
            Map.entry("40048", ScanRiskLevel.HIGH),  // Remote Code Execution (React2Shell)
            Map.entry("90019", ScanRiskLevel.HIGH),  // Server Side Code Injection
            Map.entry("90020", ScanRiskLevel.HIGH),  // Remote OS Command Injection
            Map.entry("90021", ScanRiskLevel.HIGH),  // XPath Injection
            Map.entry("90023", ScanRiskLevel.HIGH),  // XML External Entity Attack
            Map.entry("90024", ScanRiskLevel.HIGH),  // Generic Padding Oracle
            Map.entry("90029", ScanRiskLevel.HIGH),  // SOAP XML Injection
            Map.entry("90034", ScanRiskLevel.HIGH),  // Cloud Metadata Potentially Exposed
            Map.entry("90035", ScanRiskLevel.HIGH),  // Server Side Template Injection
            Map.entry("90036", ScanRiskLevel.HIGH),  // Server Side Template Injection (Blind)
            Map.entry("90037", ScanRiskLevel.HIGH),  // Remote OS Command Injection (Time Based)
            Map.entry("30001", ScanRiskLevel.HIGH),  // Buffer Overflow
            Map.entry("30002", ScanRiskLevel.HIGH),  // Format String Error
            Map.entry("90017", ScanRiskLevel.HIGH),  // XSLT Injection
            Map.entry("100043", ScanRiskLevel.HIGH), // Swagger UI Secret & Vulnerability Detector

            // MEDIUM: fuga de informacion, tampering, XSS reflejado, DoS
            Map.entry("10045", ScanRiskLevel.MEDIUM), // Source Code Disclosure - /WEB-INF Folder
            Map.entry("20017", ScanRiskLevel.MEDIUM), // Source Code Disclosure - CVE-2012-1823
            Map.entry("20019", ScanRiskLevel.MEDIUM), // External Redirect
            Map.entry("40012", ScanRiskLevel.MEDIUM), // Cross Site Scripting (Reflected)
            Map.entry("10047", ScanRiskLevel.MEDIUM), // HTTPS Content Available via HTTP
            Map.entry("10106", ScanRiskLevel.MEDIUM), // HTTP Only Site
            Map.entry("40003", ScanRiskLevel.MEDIUM), // CRLF Injection
            Map.entry("40008", ScanRiskLevel.MEDIUM), // Parameter Tampering
            Map.entry("40028", ScanRiskLevel.MEDIUM), // ELMAH Information Leak
            Map.entry("40029", ScanRiskLevel.MEDIUM), // Trace.axd Information Leak
            Map.entry("40032", ScanRiskLevel.MEDIUM), // .htaccess Information Leak
            Map.entry("40035", ScanRiskLevel.MEDIUM), // Hidden File Finder
            Map.entry("40042", ScanRiskLevel.MEDIUM), // Spring Actuator Information Leak
            Map.entry("40044", ScanRiskLevel.MEDIUM), // Exponential Entity Expansion (Billion Laughs)
            Map.entry("50000", ScanRiskLevel.MEDIUM), // Script Active Scan Rules
            Map.entry("90026", ScanRiskLevel.MEDIUM), // SOAP Action Spoofing

            // LOW: hallazgos informativos / de higiene de configuracion
            Map.entry("0", ScanRiskLevel.LOW),      // Directory Browsing
            Map.entry("10058", ScanRiskLevel.LOW),  // GET for POST
            Map.entry("10104", ScanRiskLevel.LOW)   // User Agent Fuzzer
    );

    private ZapScannerRiskCatalog() {
    }

    public static Set<String> scannerIdsForRiskLevels(Set<ScanRiskLevel> riskLevels) {
        return SCANNER_RISK_BY_ID.entrySet().stream()
                .filter(entry -> riskLevels.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
