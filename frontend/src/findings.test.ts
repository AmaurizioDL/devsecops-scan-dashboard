import { describe, expect, it } from 'vitest';
import { filterFindings, formatDuration, normalizeSeverity, owaspKey, summarize } from './findings';
import type { Finding } from './types';

function finding(overrides: Partial<Finding>): Finding {
  return {
    id: 1, targetUrl: 'http://juice-shop:3000', scanType: 'ACTIVE', alertName: 'Alert',
    riskLevel: 'Low', cweId: null, owaspCategory: null, affectedUrl: null, description: null,
    solution: null, confidence: null, pluginId: null, evidence: null, reference: null,
    detectedAt: null, ...overrides,
  };
}

describe('normalizeSeverity', () => {
  it('maps ZAP risk names case-insensitively and treats unknown values as Informational', () => {
    expect(normalizeSeverity('HIGH')).toBe('High');
    expect(normalizeSeverity(' medium ')).toBe('Medium');
    expect(normalizeSeverity('Info')).toBe('Informational');
    expect(normalizeSeverity(null)).toBe('Informational');
  });
});

describe('owaspKey', () => {
  it('keeps known Top 10 codes and maps anything else to Unmapped', () => {
    expect(owaspKey(finding({ owaspCategory: 'A05:2021' }))).toBe('A05:2021');
    expect(owaspKey(finding({ owaspCategory: 'N/A' }))).toBe('Unmapped');
    expect(owaspKey(finding({ owaspCategory: null }))).toBe('Unmapped');
  });
});

describe('summarize', () => {
  it('counts by severity and OWASP, and consolidates unique vulnerabilities by plugin + risk', () => {
    const summary = summarize([
      finding({ pluginId: '10038', riskLevel: 'Medium', owaspCategory: 'A05:2021', affectedUrl: '/a' }),
      finding({ pluginId: '10038', riskLevel: 'Medium', owaspCategory: 'A05:2021', affectedUrl: '/b' }),
      finding({ pluginId: '10098', riskLevel: 'Medium', owaspCategory: 'A01:2021' }),
      finding({ alertName: 'Legacy row', riskLevel: 'High' }),
    ]);
    expect(summary.total).toBe(4);
    expect(summary.uniqueVulnerabilities).toBe(3);
    expect(summary.bySeverity).toEqual({ High: 1, Medium: 3, Low: 0, Informational: 0 });
    expect(summary.byOwasp.map((o) => [o.key, o.count])).toEqual([
      ['A05:2021', 2], ['A01:2021', 1], ['Unmapped', 1],
    ]);
  });
});

describe('filterFindings', () => {
  const findings = [
    finding({ id: 1, riskLevel: 'Low', alertName: 'Cookie without flag', detectedAt: '2026-10-14T10:00:00' }),
    finding({ id: 2, riskLevel: 'High', alertName: 'SQL Injection', affectedUrl: 'http://x/rest/products', cweId: 89 }),
    finding({ id: 3, riskLevel: 'Low', alertName: 'Timestamp disclosure', detectedAt: '2026-10-15T10:00:00' }),
  ];
  const none = { severity: '' as const, owasp: '', search: '' };

  it('sorts by severity first, then newest first', () => {
    expect(filterFindings(findings, none).map((f) => f.id)).toEqual([2, 3, 1]);
  });

  it('filters by severity and searches alert name, URL and CWE', () => {
    expect(filterFindings(findings, { ...none, severity: 'Low' }).map((f) => f.id)).toEqual([3, 1]);
    expect(filterFindings(findings, { ...none, search: 'products' }).map((f) => f.id)).toEqual([2]);
    expect(filterFindings(findings, { ...none, search: 'CWE-89' }).map((f) => f.id)).toEqual([2]);
  });
});

describe('formatDuration', () => {
  it('formats seconds and handles an unknown ETA', () => {
    expect(formatDuration(42)).toBe('42s');
    expect(formatDuration(125)).toBe('2m 05s');
    expect(formatDuration(null)).toBe('—');
  });
});
