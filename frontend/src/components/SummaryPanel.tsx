import { useState } from 'react';
import { SEVERITIES, type Summary } from '../findings';
import SeverityBadge from './SeverityBadge';

interface Props {
  summary: Summary;
}

export default function SummaryPanel({ summary }: Props) {
  const [showTable, setShowTable] = useState(false);
  const [hovered, setHovered] = useState<string | null>(null);
  const max = Math.max(1, ...summary.byOwasp.map((o) => o.count));

  return (
    <section className="card" aria-labelledby="summary-title">
      <h2 id="summary-title">Summary</h2>
      <div className="stat-grid">
        <div className="stat">
          <span className="stat-label">Findings</span>
          <span className="stat-value">{summary.total}</span>
          <span className="stat-hint muted">one per alert and URL</span>
        </div>
        <div className="stat">
          <span className="stat-label">Unique vulnerabilities</span>
          <span className="stat-value">{summary.uniqueVulnerabilities}</span>
          <span className="stat-hint muted">grouped as in the reports</span>
        </div>
        {SEVERITIES.map((severity) => (
          <div className="stat" key={severity}>
            <span className="stat-label"><SeverityBadge severity={severity} /></span>
            <span className="stat-value">{summary.bySeverity[severity]}</span>
          </div>
        ))}
      </div>

      <div className="chart-header">
        <h3>Findings by OWASP Top 10 category</h3>
        {summary.byOwasp.length > 0 && (
          <button type="button" className="link-button" onClick={() => setShowTable((v) => !v)}>
            {showTable ? 'Show chart' : 'Show as table'}
          </button>
        )}
      </div>

      {summary.byOwasp.length === 0 ? (
        <p className="muted">No findings yet.</p>
      ) : showTable ? (
        <table className="data-table compact">
          <thead>
            <tr><th scope="col">Category</th><th scope="col" className="num">Findings</th></tr>
          </thead>
          <tbody>
            {summary.byOwasp.map((o) => (
              <tr key={o.key}><td>{o.label}</td><td className="num">{o.count}</td></tr>
            ))}
          </tbody>
        </table>
      ) : (
        <ul className="bar-chart" aria-label="Findings by OWASP Top 10 category">
          {summary.byOwasp.map((o) => {
            const share = Math.round((o.count / summary.total) * 100);
            return (
              <li
                key={o.key}
                className={`bar-row${hovered && hovered !== o.key ? ' dimmed' : ''}`}
                onMouseEnter={() => setHovered(o.key)}
                onMouseLeave={() => setHovered(null)}
                tabIndex={0}
                onFocus={() => setHovered(o.key)}
                onBlur={() => setHovered(null)}
                aria-label={`${o.label}: ${o.count} findings, ${share}% of total`}
              >
                <span className="bar-label">{o.label}</span>
                <span className="bar-track">
                  <span
                    className={`bar${o.key === 'Unmapped' ? ' bar-muted' : ''}`}
                    style={{ width: `${(o.count / max) * 100}%` }}
                  />
                  <span className="bar-value">{o.count}</span>
                  {hovered === o.key && (
                    <span className="tooltip" role="tooltip">
                      <strong>{o.label}</strong>
                      <br />
                      {o.count} findings · {share}% of total
                    </span>
                  )}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
