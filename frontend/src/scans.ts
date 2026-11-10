import type { Severity } from './findings';
import type { ScanRecord, ScanStatus } from './types';

export const STATUS_LABELS: Record<ScanStatus, string> = {
  RUNNING: 'Running',
  DONE: 'Finished',
  STOPPED: 'Stopped',
  FAILED: 'Failed',
};

export function newBySeverity(scan: ScanRecord): Record<Severity, number> {
  return { High: scan.newHigh, Medium: scan.newMedium, Low: scan.newLow, Informational: scan.newInformational };
}

export function newFindingsTotal(scan: ScanRecord): number {
  return scan.newHigh + scan.newMedium + scan.newLow + scan.newInformational;
}

/** Seconds from start to finish, or to `now` while the scan is still running. */
export function scanDurationSeconds(scan: ScanRecord, now: Date = new Date()): number {
  const end = scan.finishedAt ? new Date(scan.finishedAt) : now;
  return Math.max(0, Math.round((end.getTime() - new Date(scan.startedAt).getTime()) / 1000));
}

/** "High, Low" for the active-scan rules that ran; "All rules" when none were picked. */
export function riskLevelsLabel(scan: ScanRecord): string {
  const levels = (scan.riskLevels ?? '').split(',').map((l) => l.trim()).filter(Boolean);
  if (levels.length === 0) return 'All rules';
  return levels.map((l) => l.charAt(0) + l.slice(1).toLowerCase()).join(', ');
}

/** Short, stable label for a scan in charts and headings (#n counts from the oldest scan). */
export function scanNumber(scan: ScanRecord, history: ScanRecord[]): number {
  const oldestFirst = [...history].sort((a, b) => a.startedAt.localeCompare(b.startedAt));
  return oldestFirst.findIndex((s) => s.id === scan.id) + 1;
}

/** The last `limit` scans that are no longer running, oldest first (left to right in a chart). */
export function trendScans(history: ScanRecord[], limit: number): ScanRecord[] {
  return history
    .filter((s) => s.status !== 'RUNNING')
    .sort((a, b) => a.startedAt.localeCompare(b.startedAt))
    .slice(-limit);
}

/** Viewer-locale date and time, without seconds. */
export function formatDateTime(value: string | null): string {
  return value ? new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—';
}
