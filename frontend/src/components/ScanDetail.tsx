import { useEffect, useMemo, useState } from 'react';
import { fetchFindings, reportUrl } from '../api';
import { formatDuration, SEVERITIES } from '../findings';
import { routeHref } from '../route';
import {
  formatDateTime, newBySeverity, newFindingsTotal, riskLevelsLabel, scanDurationSeconds, scanNumber,
} from '../scans';
import type { Finding, ScanRecord } from '../types';
import FindingsTable from './FindingsTable';
import { BackIcon, DownloadIcon } from './Icons';
import { StatusBadge } from './ScanBits';
import SeverityBadge from './SeverityBadge';

interface Props {
  scanId: string;
  history: ScanRecord[];
  historyLoading: boolean;
}

export default function ScanDetail({ scanId, history, historyLoading }: Props) {
  const scan = useMemo(() => history.find((s) => s.id === scanId), [history, scanId]);
  const [findings, setFindings] = useState<Finding[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Re-fetch when the scan's status changes (a running scan keeps adding findings).
  const status = scan?.status;
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    (async () => {
      try {
        const result = await fetchFindings(scanId);
        if (cancelled) return;
        setFindings(result);
        setError(null);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [scanId, status]);

  const back = (
    <a className="back-link" href={routeHref({ view: 'scans' })}><BackIcon size={16} /> All scans</a>
  );

  if (!scan) {
    return (
      <div className="stack">
        {back}
        <section className="card">
          <p className="muted">{historyLoading ? 'Loading scan…' : 'This scan is not in the history.'}</p>
        </section>
      </div>
    );
  }

  const counts = newBySeverity(scan);

  return (
    <div className="stack">
      {back}
      <section className="card" aria-labelledby="scan-detail-title">
        <div className="card-header">
          <h2 id="scan-detail-title">Scan #{scanNumber(scan, history)} <StatusBadge status={scan.status} /></h2>
          <div className="button-row">
            <a className="button-link secondary" href={reportUrl('executive', { scanId: scan.id })} download>
              <DownloadIcon size={14} /> Executive PDF
            </a>
            <a className="button-link secondary" href={reportUrl('technical', { scanId: scan.id })} download>
              <DownloadIcon size={14} /> Technical PDF
            </a>
          </div>
        </div>
        <dl className="meta-grid">
          <div><dt>Target</dt><dd className="truncate" title={scan.targetUrl}>{scan.targetUrl}</dd></div>
          <div><dt>Active-scan rules</dt><dd>{riskLevelsLabel(scan)}</dd></div>
          <div><dt>Started</dt><dd>{formatDateTime(scan.startedAt)}</dd></div>
          <div><dt>Finished</dt><dd>{formatDateTime(scan.finishedAt)}</dd></div>
          <div><dt>Duration</dt><dd>{formatDuration(scanDurationSeconds(scan))}</dd></div>
          <div><dt>New findings</dt><dd>{newFindingsTotal(scan)}</dd></div>
        </dl>
        {scan.errorMessage && <div className="alert" role="alert">{scan.errorMessage}</div>}
        <div className="severity-strip">
          {SEVERITIES.map((s) => (
            <div key={s} className={`kpi kpi-${s.toLowerCase()}`}>
              <span className="kpi-label"><SeverityBadge severity={s} /></span>
              <span className="kpi-value">{counts[s]}</span>
            </div>
          ))}
        </div>
        <p className="muted small">
          Findings are stored once per target, rule, risk and URL, so this scan lists only what it found for the
          first time. Issues already known from earlier scans are not repeated here.
        </p>
      </section>

      {error && <div className="alert" role="alert">Could not load this scan's findings: {error}</div>}
      <FindingsTable findings={findings} history={history} loading={loading} />
    </div>
  );
}
