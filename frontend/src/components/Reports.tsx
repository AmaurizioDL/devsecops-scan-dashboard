import { useState } from 'react';
import { reportUrl, type ReportScope } from '../api';
import { GRAFANA_URL } from '../config';
import { targetsOf } from '../findings';
import { formatDateTime, scanNumber } from '../scans';
import type { Finding, ScanRecord } from '../types';
import { DownloadIcon, ExternalIcon } from './Icons';

interface Props {
  findings: Finding[];
  history: ScanRecord[];
}

/** The scope select's value: "all", "target:<url>" or "scan:<id>". */
function toScope(value: string): ReportScope {
  if (value.startsWith('target:')) return { targetUrl: value.slice('target:'.length) };
  if (value.startsWith('scan:')) return { scanId: value.slice('scan:'.length) };
  return {};
}

export default function Reports({ findings, history }: Props) {
  const [scopeValue, setScopeValue] = useState('all');
  const scope = toScope(scopeValue);
  const targets = targetsOf(findings);
  const finishedScans = history.filter((s) => s.status !== 'RUNNING');

  return (
    <div className="stack">
      <section className="card" aria-labelledby="scope-title">
        <h2 id="scope-title">Report scope</h2>
        <label className="field scope-field">
          <span>Findings to include</span>
          <select value={scopeValue} onChange={(e) => setScopeValue(e.target.value)}>
            <option value="all">All findings ({findings.length})</option>
            {targets.length > 0 && (
              <optgroup label="One target">
                {targets.map((t) => <option key={t} value={`target:${t}`}>{t}</option>)}
              </optgroup>
            )}
            {finishedScans.length > 0 && (
              <optgroup label="New findings of one scan">
                {finishedScans.map((s) => (
                  <option key={s.id} value={`scan:${s.id}`}>
                    Scan #{scanNumber(s, history)} · {formatDateTime(s.startedAt)}
                  </option>
                ))}
              </optgroup>
            )}
          </select>
        </label>
        {findings.length === 0 && <p className="muted">No findings yet: the reports will be empty until you run a scan.</p>}
      </section>

      <div className="grid-3">
        <section className="card report-card" aria-labelledby="executive-title">
          <h2 id="executive-title">Executive summary</h2>
          <p className="muted">
            Two to three pages for management: overall risk posture, top risks in business language,
            strategic recommendations and a remediation roadmap.
          </p>
          <a className="button-link" href={reportUrl('executive', scope)} download>
            <DownloadIcon size={14} /> Executive report (PDF)
          </a>
        </section>
        <section className="card report-card" aria-labelledby="technical-title">
          <h2 id="technical-title">Technical report</h2>
          <p className="muted">
            One entry per consolidated vulnerability, with CWE, OWASP category, affected URLs, evidence,
            the fix and references, plus methodology appendices.
          </p>
          <a className="button-link" href={reportUrl('technical', scope)} download>
            <DownloadIcon size={14} /> Technical report (PDF)
          </a>
        </section>
        <section className="card report-card" aria-labelledby="grafana-title">
          <h2 id="grafana-title">Grafana</h2>
          <p className="muted">Historical dashboards over the same findings database, provisioned as code.</p>
          <a className="button-link secondary" href={GRAFANA_URL} target="_blank" rel="noreferrer">
            Open Grafana <ExternalIcon size={14} />
          </a>
        </section>
      </div>
    </div>
  );
}
