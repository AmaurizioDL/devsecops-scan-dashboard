import type { Finding } from './types';

export const SEVERITIES = ['High', 'Medium', 'Low', 'Informational'] as const;
export type Severity = (typeof SEVERITIES)[number];

/** ZAP reports "Informational" but some rows say "Info"; anything unknown counts as Informational. */
export function normalizeSeverity(riskLevel: string | null | undefined): Severity {
  const value = (riskLevel ?? '').trim().toLowerCase();
  if (value === 'high') return 'High';
  if (value === 'medium') return 'Medium';
  if (value === 'low') return 'Low';
  return 'Informational';
}

export function severityRank(severity: Severity): number {
  return SEVERITIES.indexOf(severity);
}

export const OWASP_TOP10: Record<string, string> = {
  'A01:2021': 'Broken Access Control',
  'A02:2021': 'Cryptographic Failures',
  'A03:2021': 'Injection',
  'A04:2021': 'Insecure Design',
  'A05:2021': 'Security Misconfiguration',
  'A06:2021': 'Vulnerable and Outdated Components',
  'A07:2021': 'Identification and Authentication Failures',
  'A08:2021': 'Software and Data Integrity Failures',
  'A09:2021': 'Security Logging and Monitoring Failures',
  'A10:2021': 'Server-Side Request Forgery',
};

export const UNMAPPED = 'Unmapped';

/** Returns the OWASP code (e.g. "A05:2021") or UNMAPPED when the finding has no Top 10 category. */
export function owaspKey(finding: Finding): string {
  const code = finding.owaspCategory?.trim();
  return code && OWASP_TOP10[code] ? code : UNMAPPED;
}

export function owaspLabel(key: string): string {
  return OWASP_TOP10[key] ? `${key.slice(0, 3)} ${OWASP_TOP10[key]}` : 'Not mapped to OWASP Top 10';
}

export interface Summary {
  total: number;
  uniqueVulnerabilities: number;
  bySeverity: Record<Severity, number>;
  /** Sorted by count desc, then by OWASP code. */
  byOwasp: { key: string; label: string; count: number }[];
}

/** Same grouping as the backend's VulnerabilityConsolidator: plugin id (or alert name) + risk. */
export function vulnerabilityKey(finding: Finding): string {
  return `${finding.pluginId || finding.alertName}|${normalizeSeverity(finding.riskLevel)}`;
}

export function summarize(findings: Finding[]): Summary {
  const bySeverity: Record<Severity, number> = { High: 0, Medium: 0, Low: 0, Informational: 0 };
  const owaspCounts = new Map<string, number>();
  const unique = new Set<string>();
  for (const finding of findings) {
    bySeverity[normalizeSeverity(finding.riskLevel)]++;
    const key = owaspKey(finding);
    owaspCounts.set(key, (owaspCounts.get(key) ?? 0) + 1);
    unique.add(vulnerabilityKey(finding));
  }
  const byOwasp = [...owaspCounts.entries()]
    .map(([key, count]) => ({ key, label: owaspLabel(key), count }))
    .sort((a, b) => b.count - a.count || a.key.localeCompare(b.key));
  return { total: findings.length, uniqueVulnerabilities: unique.size, bySeverity, byOwasp };
}

export interface FindingFilters {
  severity: Severity | '';
  owasp: string;
  search: string;
}

export function filterFindings(findings: Finding[], filters: FindingFilters): Finding[] {
  const search = filters.search.trim().toLowerCase();
  return findings
    .filter((f) => !filters.severity || normalizeSeverity(f.riskLevel) === filters.severity)
    .filter((f) => !filters.owasp || owaspKey(f) === filters.owasp)
    .filter(
      (f) =>
        !search ||
        [f.alertName, f.affectedUrl, f.targetUrl, f.cweId != null ? `cwe-${f.cweId}` : '']
          .some((value) => value?.toLowerCase().includes(search)),
    )
    .sort(
      (a, b) =>
        severityRank(normalizeSeverity(a.riskLevel)) - severityRank(normalizeSeverity(b.riskLevel)) ||
        (b.detectedAt ?? '').localeCompare(a.detectedAt ?? ''),
    );
}

export function formatDuration(totalSeconds: number | null | undefined): string {
  if (totalSeconds == null || totalSeconds < 0) return '—';
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = Math.round(totalSeconds % 60);
  return minutes > 0 ? `${minutes}m ${seconds.toString().padStart(2, '0')}s` : `${seconds}s`;
}
