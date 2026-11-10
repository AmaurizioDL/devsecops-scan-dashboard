import { useState } from 'react';
import type { Summary } from '../findings';

interface Props {
  summary: Summary;
}

/** Findings per OWASP Top 10 category, one horizontal bar each, with a table view. */
export default function OwaspChart({ summary }: Props) {
  const [showTable, setShowTable] = useState(false);
  const [hovered, setHovered] = useState<string | null>(null);
  const max = Math.max(1, ...summary.byOwasp.map((o) => o.count));

  if (summary.byOwasp.length === 0) {
    return <p className="muted">No findings yet.</p>;
  }

  return (
    <div>
      <div className="chart-toolbar">
        <span className="muted small">Findings per category</span>
        <button type="button" className="link-button" onClick={() => setShowTable((v) => !v)}>
          {showTable ? 'Show chart' : 'Show as table'}
        </button>
      </div>

      {showTable ? (
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
    </div>
  );
}
