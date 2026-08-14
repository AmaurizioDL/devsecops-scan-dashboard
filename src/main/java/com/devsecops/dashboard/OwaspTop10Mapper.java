package com.devsecops.dashboard;

import java.util.Map;

import static com.devsecops.dashboard.OwaspTop10Category.A01_BROKEN_ACCESS_CONTROL;
import static com.devsecops.dashboard.OwaspTop10Category.A02_CRYPTOGRAPHIC_FAILURES;
import static com.devsecops.dashboard.OwaspTop10Category.A03_INJECTION;
import static com.devsecops.dashboard.OwaspTop10Category.A04_INSECURE_DESIGN;
import static com.devsecops.dashboard.OwaspTop10Category.A05_SECURITY_MISCONFIGURATION;
import static com.devsecops.dashboard.OwaspTop10Category.A07_IDENTIFICATION_AUTH_FAILURES;
import static com.devsecops.dashboard.OwaspTop10Category.A08_SOFTWARE_DATA_INTEGRITY_FAILURES;
import static com.devsecops.dashboard.OwaspTop10Category.A10_SSRF;
import static com.devsecops.dashboard.OwaspTop10Category.UNMAPPED;

/**
 * Mapea el cwe_id que ZAP reporta en cada alerta a una categoria del OWASP
 * Top 10:2021 (https://owasp.org/Top10/), usando el mapeo oficial de OWASP
 * (CWE -> categoria) recortado a los CWEs que los scanners de este proyecto
 * (ver ZapScannerRiskCatalog) pueden llegar a reportar. Un cwe_id nulo o que
 * no aparece aqui cae en UNMAPPED en vez de fallar.
 */
public final class OwaspTop10Mapper {

    private static final Map<Integer, OwaspTop10Category> CATEGORY_BY_CWE = Map.ofEntries(
            // A01: Broken Access Control
            Map.entry(22, A01_BROKEN_ACCESS_CONTROL),   // Path Traversal
            Map.entry(23, A01_BROKEN_ACCESS_CONTROL),   // Relative Path Traversal
            Map.entry(200, A01_BROKEN_ACCESS_CONTROL),  // Exposure of Sensitive Information
            Map.entry(284, A01_BROKEN_ACCESS_CONTROL),  // Improper Access Control
            Map.entry(285, A01_BROKEN_ACCESS_CONTROL),  // Improper Authorization
            Map.entry(352, A01_BROKEN_ACCESS_CONTROL),  // CSRF
            Map.entry(538, A01_BROKEN_ACCESS_CONTROL),  // File and Directory Information Exposure
            Map.entry(548, A01_BROKEN_ACCESS_CONTROL),  // Directory Listing / Browsing
            Map.entry(601, A01_BROKEN_ACCESS_CONTROL),  // Open Redirect
            Map.entry(639, A01_BROKEN_ACCESS_CONTROL),  // Insecure Direct Object Reference

            // A02: Cryptographic Failures
            Map.entry(319, A02_CRYPTOGRAPHIC_FAILURES), // Cleartext Transmission of Sensitive Information
            Map.entry(326, A02_CRYPTOGRAPHIC_FAILURES), // Inadequate Encryption Strength
            Map.entry(327, A02_CRYPTOGRAPHIC_FAILURES), // Use of a Broken/Risky Crypto Algorithm
            Map.entry(330, A02_CRYPTOGRAPHIC_FAILURES), // Use of Insufficiently Random Values
            Map.entry(759, A02_CRYPTOGRAPHIC_FAILURES), // Use of a One-Way Hash without a Salt
            Map.entry(916, A02_CRYPTOGRAPHIC_FAILURES), // Use of Password Hash With Insufficient Effort

            // A03: Injection
            Map.entry(78, A03_INJECTION),   // OS Command Injection
            Map.entry(79, A03_INJECTION),   // Cross-Site Scripting (reflejado, persistente, DOM)
            Map.entry(89, A03_INJECTION),   // SQL Injection
            Map.entry(90, A03_INJECTION),   // LDAP Injection
            Map.entry(91, A03_INJECTION),   // XML Injection (incluye SOAP/XSLT injection)
            Map.entry(93, A03_INJECTION),   // CRLF Injection
            Map.entry(94, A03_INJECTION),   // Code Injection (SSTI, Spring4Shell, React2Shell, etc.)
            Map.entry(97, A03_INJECTION),   // Server-Side Includes Injection
            Map.entry(98, A03_INJECTION),   // Remote File Inclusion
            Map.entry(120, A03_INJECTION),  // Buffer Overflow
            Map.entry(134, A03_INJECTION),  // Format String Vulnerability
            Map.entry(643, A03_INJECTION),  // XPath Injection
            Map.entry(776, A03_INJECTION),  // XML Entity Expansion (Billion Laughs)

            // A04: Insecure Design
            Map.entry(209, A04_INSECURE_DESIGN), // Information Exposure Through Error Message
            Map.entry(346, A04_INSECURE_DESIGN), // Origin Validation Error (SOAP Action Spoofing)
            Map.entry(472, A04_INSECURE_DESIGN), // External Control of Assumed-Immutable Web Parameter

            // A05: Security Misconfiguration
            Map.entry(16, A05_SECURITY_MISCONFIGURATION),   // Configuration
            Map.entry(611, A05_SECURITY_MISCONFIGURATION),  // XML External Entity (XXE) Reference
            Map.entry(614, A05_SECURITY_MISCONFIGURATION),  // Sensitive Cookie Without 'Secure' Attribute
            Map.entry(693, A05_SECURITY_MISCONFIGURATION),  // Protection Mechanism Failure (missing CSP)
            Map.entry(942, A05_SECURITY_MISCONFIGURATION),  // Permissive Cross-domain Policy (CORS)
            Map.entry(1004, A05_SECURITY_MISCONFIGURATION), // Sensitive Cookie Without 'HttpOnly' Flag
            Map.entry(1021, A05_SECURITY_MISCONFIGURATION), // Improper Restriction of Rendered UI Layers (Clickjacking)

            // A07: Identification and Authentication Failures
            Map.entry(798, A07_IDENTIFICATION_AUTH_FAILURES), // Use of Hard-coded Credentials

            // A08: Software and Data Integrity Failures
            Map.entry(502, A08_SOFTWARE_DATA_INTEGRITY_FAILURES), // Deserialization of Untrusted Data (Log4Shell)
            Map.entry(829, A08_SOFTWARE_DATA_INTEGRITY_FAILURES), // Inclusion of Functionality from Untrusted Control Sphere

            // A10: Server-Side Request Forgery
            Map.entry(918, A10_SSRF) // SSRF (Cloud Metadata Exposure)
    );

    private OwaspTop10Mapper() {
    }

    public static OwaspTop10Category mapCwe(Integer cweId) {
        if (cweId == null) {
            return UNMAPPED;
        }
        return CATEGORY_BY_CWE.getOrDefault(cweId, UNMAPPED);
    }
}
