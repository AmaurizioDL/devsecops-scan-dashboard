import { SEVERITIES } from '../findings';
import { newBySeverity, STATUS_LABELS } from '../scans';
import type { ScanRecord, ScanStatus } from '../types';
import SeverityBadge from './SeverityBadge';

// Scan status is a state, so like severity it ships with an icon and a text label.
const STATUS_ICONS: Record<ScanStatus, string> = {
  RUNNING: '◌',
  DONE: '✓',
  STOPPED: '■',
  FAILED: '✕',
};

export function StatusBadge({ status }: { status: ScanStatus }) {
  return (
    <span className={`status status-${status.toLowerCase()}`}>
      <span aria-hidden="true">{STATUS_ICONS[status]}</span>
      {STATUS_LABELS[status]}
    </span>
  );
}

/** Compact per-severity counts of a scan's new findings; zero counts are left out. */
export function NewCounts({ scan }: { scan: ScanRecord }) {
  const counts = newBySeverity(scan);
  const present = SEVERITIES.filter((s) => counts[s] > 0);
  if (present.length === 0) return <span className="muted">None</span>;
  return (
    <span className="new-counts">
      {present.map((s) => (
        <span key={s} className="new-count" title={`${counts[s]} ${s}`}>
          <SeverityBadge severity={s} compact />
          <span className="num">{counts[s]}</span>
        </span>
      ))}
    </span>
  );
}
