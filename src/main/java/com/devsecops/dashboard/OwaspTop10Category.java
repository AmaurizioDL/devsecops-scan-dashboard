package com.devsecops.dashboard;

/**
 * Categorias del OWASP Top 10:2021 (https://owasp.org/Top10/), usadas para dar
 * contexto de negocio y remediacion generica a un finding cuando el propio ZAP
 * no trae una solucion util. UNMAPPED cubre findings sin CWE o con un CWE que
 * no aparece en OwaspTop10Mapper. Los textos estan en ingles porque se imprimen
 * tal cual en los reportes PDF.
 */
public enum OwaspTop10Category {

    A01_BROKEN_ACCESS_CONTROL(
            "A01:2021",
            "Broken Access Control",
            "An attacker can read, modify or delete data and functions outside their permissions, which can lead to exposure of other users' data or unauthorized administrative actions.",
            "Enforce access control on the server side (deny by default), validate resource ownership on every request, and add CSRF protection to state-changing operations."),

    A02_CRYPTOGRAPHIC_FAILURES(
            "A02:2021",
            "Cryptographic Failures",
            "Sensitive data (credentials, sessions, personal information) can be exposed in transit or at rest when sent unencrypted or protected with weak algorithms, enabling identity theft and regulatory non-compliance.",
            "Enforce HTTPS/TLS on every route, use modern encryption and hashing algorithms (e.g. bcrypt/argon2 for passwords), and never transmit or log sensitive data in clear text."),

    A03_INJECTION(
            "A03:2021",
            "Injection",
            "Untrusted input interpreted as code (SQL, OS commands, templates) can let an attacker read or modify the database, run commands on the server, or fully compromise it.",
            "Use parameterized queries / prepared statements, validate and sanitize all user input, and never concatenate user input into commands, queries or templates."),

    A04_INSECURE_DESIGN(
            "A04:2021",
            "Insecure Design",
            "Design flaws (missing rate limits, absent business-rule validation, internal details exposed in errors) enable functional abuse that a one-off code fix does not solve at the root.",
            "Review the affected flow with threat modeling, add rate and size limits where applicable, and avoid exposing internal details (stack traces, file paths) in error responses."),

    A05_SECURITY_MISCONFIGURATION(
            "A05:2021",
            "Security Misconfiguration",
            "Default configuration, unnecessarily exposed services or panels, or misconfigured XML parsers (XXE) give an attacker information or access that should not be available.",
            "Harden default configuration (disable diagnostic panels/endpoints in production, disable external entities in XML parsers), and keep security headers (CSP, X-Frame-Options, HSTS) enabled."),

    A06_VULNERABLE_OUTDATED_COMPONENTS(
            "A06:2021",
            "Vulnerable and Outdated Components",
            "Components or dependencies with known, public vulnerabilities hand an attacker an already-documented exploit against the application, without needing to find the bug themselves.",
            "Inventory dependencies (backend and frontend), upgrade to versions without known CVEs, and retire unmaintained components."),

    A07_IDENTIFICATION_AUTH_FAILURES(
            "A07:2021",
            "Identification and Authentication Failures",
            "Poorly protected sessions or credentials (session fixation, no expiration, default/hardcoded credentials) let an attacker impersonate a legitimate user.",
            "Invalidate and rotate the session ID after login, expire idle sessions, remove hardcoded/default credentials, and add brute-force protection (rate limiting, MFA)."),

    A08_SOFTWARE_DATA_INTEGRITY_FAILURES(
            "A08:2021",
            "Software and Data Integrity Failures",
            "Deserializing untrusted data or loading code/dependencies without verifying their integrity can give an attacker arbitrary code execution on the server.",
            "Avoid deserializing data from untrusted sources without validation, and verify the integrity (signatures/checksums) of dependencies and updates before using them."),

    A09_LOGGING_MONITORING_FAILURES(
            "A09:2021",
            "Security Logging and Monitoring Failures",
            "Without sufficient logging and alerting, an ongoing (or already successful) attack can go unnoticed for a long time, increasing the damage and hindering incident response.",
            "Log relevant security events (failed logins, permission changes, validation errors) with enough context, and alert on anomalous patterns in real time."),

    A10_SSRF(
            "A10:2021",
            "Server-Side Request Forgery (SSRF)",
            "If the server can be induced to make requests to attacker-chosen URLs, the attacker can reach internal systems (cloud metadata, admin services) that are not directly exposed to the Internet.",
            "Validate and restrict (allowlist) the destinations the server may send outbound requests to, and network-isolate internal services that should not be reachable from the application."),

    UNMAPPED(
            "N/A",
            "No associated OWASP Top 10 category",
            "This finding has no CWE recognized by the project's mapping catalog, so it could not be automatically associated with an OWASP Top 10:2021 category. Review its impact manually.",
            "Review the description and solution originally reported by the scanner to define the appropriate remediation.");

    private final String code;
    private final String title;
    private final String businessImpact;
    private final String defaultRemediation;

    OwaspTop10Category(String code, String title, String businessImpact, String defaultRemediation) {
        this.code = code;
        this.title = title;
        this.businessImpact = businessImpact;
        this.defaultRemediation = defaultRemediation;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String businessImpact() {
        return businessImpact;
    }

    public String defaultRemediation() {
        return defaultRemediation;
    }

    public String label() {
        return code + " - " + title;
    }
}
