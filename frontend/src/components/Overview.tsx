import { formatDuration, SEVERITIES, type Summary } from '../findings';
import { routeHref } from '../route';
import { scanDurationSeconds, scanNumber } from '../scans';
import type { ScanRecord } from '../types';
import OwaspChart from './OwaspChart';
import { StatusBadge } from './ScanBits';
import ScanHistoryTable from './ScanHistoryTable';
import ScanTrendChart from './ScanTrendChart';
import SeverityBadge from './SeverityBadge';
import SeverityDonut from './SeverityDonut';

interface Props {
  summary: Summary;
  history: ScanRecord[];
}

export default function Overview({ summary, history }: Props) {
  const lastScan = history[0];

  return (
    <div className="stack">
      <section className="kpi-grid" aria-label="Key figures">
        <a className="kpi kpi-link" href={routeHref({ view: 'findings' })}>
          <span className="kpi-label">Findings</span>
          <span className="kpi-value">{summary.total}</span>
          <span className="kpi-hint muted">one per alert and URL</span>
        </a>
        <div className="kpi">
          <span className="kpi-label">Unique vulnerabilities</span>
          <span className="kpi-value">{summary.uniqueVulnerabilities}</span>
          <span className="kpi-hint muted">grouped as in the reports</span>
        </div>
        {SEVERITIES.slice(0, 2).map((severity) => (
          <div className={`kpi kpi-${severity.toLowerCase()}`} key={severity}>
            <span className="kpi-label"><SeverityBadge severity={severity} /></span>
            <span className="kpi-value">{summary.bySeverity[severity]}</span>
            <span className="kpi-hint muted">{severity === 'High' ? 'fix within 7 days' : 'fix within 30 days'}</span>
          </div>
        ))}
        <a className="kpi kpi-link" href={lastScan ? routeHref({ view: 'scans', scanId: lastScan.id }) : routeHref({ view: 'scans' })}>
          <span className="kpi-label">Last scan</span>
          {lastScan ? (
            <>
              <span className="kpi-value">#{scanNumber(lastScan, history)}</span>
              <span className="kpi-hint"><StatusBadge status={lastScan.status} /> · {formatDuration(scanDurationSeconds(lastScan))}</span>
            </>
          ) : (
            <>
              <span className="kpi-value">—</span>
              <span className="kpi-hint muted">no scans yet</span>
            </>
          )}
        </a>
      </section>

      <div className="grid-2">
        <section className="card" aria-labelledby="severity-title">
          <h2 id="severity-title">Severity mix</h2>
          <SeverityDonut counts={summary.bySeverity} />
        </section>
        <section className="card" aria-labelledby="summary-title">
          <h2 id="summary-title">OWASP Top 10 (2021)</h2>
          <OwaspChart summary={summary} />
        </section>
      </div>

      <section className="card" aria-labelledby="trend-title">
        <div className="card-header">
          <h2 id="trend-title">New findings per scan</h2>
          <p className="muted">Last 12 finished scans. Click a column to open that scan.</p>
        </div>
        <ScanTrendChart history={history} />
      </section>

      <section className="card" aria-labelledby="recent-title">
        <div className="card-header">
          <h2 id="recent-title">Recent scans</h2>
          <a href={routeHref({ view: 'scans' })}>View all scans →</a>
        </div>
        <ScanHistoryTable history={history} limit={5} />
      </section>
    </div>
  );
}
