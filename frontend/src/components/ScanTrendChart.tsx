import { useState } from 'react';
import { SEVERITIES } from '../findings';
import { routeHref } from '../route';
import { formatDateTime, newBySeverity, newFindingsTotal, scanNumber, trendScans } from '../scans';
import type { ScanRecord } from '../types';
import SeverityBadge from './SeverityBadge';

const MAX_SCANS = 12;

interface Props {
  history: ScanRecord[];
}

/** Stacked columns: how many findings each scan saw for the first time, by severity. */
export default function ScanTrendChart({ history }: Props) {
  const [showTable, setShowTable] = useState(false);
  const [hovered, setHovered] = useState<string | null>(null);
  const scans = trendScans(history, MAX_SCANS);
  const max = Math.max(1, ...scans.map(newFindingsTotal));

  if (scans.length === 0) {
    return <p className="muted">No finished scans yet. Run one from the Scans page.</p>;
  }

  return (
    <div>
      <div className="chart-toolbar">
        <ul className="chart-legend" aria-label="Legend">
          {SEVERITIES.map((s) => <li key={s}><SeverityBadge severity={s} /></li>)}
        </ul>
        <button type="button" className="link-button" onClick={() => setShowTable((v) => !v)}>
          {showTable ? 'Show chart' : 'Show as table'}
        </button>
      </div>

      {showTable ? (
        <div className="table-scroll">
          <table className="data-table compact">
            <thead>
              <tr>
                <th scope="col">Scan</th>
                {SEVERITIES.map((s) => <th key={s} scope="col" className="num">{s}</th>)}
                <th scope="col" className="num">Total</th>
              </tr>
            </thead>
            <tbody>
              {scans.map((scan) => (
                <tr key={scan.id}>
                  <td><a href={routeHref({ view: 'scans', scanId: scan.id })}>#{scanNumber(scan, history)}</a></td>
                  {SEVERITIES.map((s) => <td key={s} className="num">{newBySeverity(scan)[s]}</td>)}
                  <td className="num">{newFindingsTotal(scan)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="trend">
          <div className="trend-axis" aria-hidden="true">
            <span>{max}</span>
            <span>0</span>
          </div>
          <div className="trend-plot" role="list" aria-label="New findings per scan">
            {scans.map((scan) => {
              const counts = newBySeverity(scan);
              const total = newFindingsTotal(scan);
              const number = scanNumber(scan, history);
              const isHovered = hovered === scan.id;
              return (
                <a
                  key={scan.id}
                  role="listitem"
                  href={routeHref({ view: 'scans', scanId: scan.id })}
                  className={`trend-col${hovered && !isHovered ? ' dimmed' : ''}`}
                  onMouseEnter={() => setHovered(scan.id)}
                  onMouseLeave={() => setHovered(null)}
                  onFocus={() => setHovered(scan.id)}
                  onBlur={() => setHovered(null)}
                  aria-label={`Scan ${number}: ${total} new findings (${SEVERITIES.map((s) => `${counts[s]} ${s}`).join(', ')})`}
                >
                  <span className="trend-stack" style={{ height: `${(total / max) * 100}%` }}>
                    {/* High sits on the baseline; zero-count segments are not drawn. */}
                    {SEVERITIES.filter((s) => counts[s] > 0).map((s) => (
                      <span key={s} className={`trend-seg seg-${s.toLowerCase()}`} style={{ flexGrow: counts[s] }} />
                    ))}
                  </span>
                  <span className="trend-x">#{number}</span>
                  {isHovered && (
                    <span className="tooltip trend-tooltip" role="tooltip">
                      <span className="tooltip-title"><strong>Scan #{number}</strong> · {formatDateTime(scan.startedAt)}</span>
                      {SEVERITIES.map((s) => (
                        <span key={s} className="tooltip-row">
                          <SeverityBadge severity={s} /> <span className="num">{counts[s]}</span>
                        </span>
                      ))}
                      <span className="tooltip-row"><span>Total new</span> <strong className="num">{total}</strong></span>
                    </span>
                  )}
                </a>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
