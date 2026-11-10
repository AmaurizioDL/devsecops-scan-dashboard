import { formatDuration } from '../findings';
import { routeHref } from '../route';
import { formatDateTime, riskLevelsLabel, scanDurationSeconds, scanNumber } from '../scans';
import type { ScanRecord } from '../types';
import { NewCounts, StatusBadge } from './ScanBits';

interface Props {
  history: ScanRecord[];
  /** Show only the newest n scans (the overview's "recent scans"). */
  limit?: number;
}

export default function ScanHistoryTable({ history, limit }: Props) {
  const rows = limit ? history.slice(0, limit) : history;

  if (rows.length === 0) {
    return <p className="muted empty">No scans yet. Start one above and it will be listed here.</p>;
  }

  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr>
            <th scope="col">Scan</th>
            <th scope="col">Status</th>
            <th scope="col">Target</th>
            {!limit && <th scope="col">Rules</th>}
            <th scope="col">Started</th>
            <th scope="col" className="num">Duration</th>
            <th scope="col">New findings</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((scan) => (
            <tr key={scan.id} className="scan-row">
              <td className="nowrap">
                <a href={routeHref({ view: 'scans', scanId: scan.id })}>#{scanNumber(scan, history)}</a>
              </td>
              <td><StatusBadge status={scan.status} /></td>
              <td className="url-cell" title={scan.targetUrl}>{scan.targetUrl}</td>
              {!limit && <td className="nowrap">{riskLevelsLabel(scan)}</td>}
              <td className="nowrap">{formatDateTime(scan.startedAt)}</td>
              <td className="num nowrap">{formatDuration(scanDurationSeconds(scan))}</td>
              <td><NewCounts scan={scan} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
