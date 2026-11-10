import type { Severity } from '../findings';

// Severity is a status, so it always ships with an icon and a text label, never color alone.
const ICONS: Record<Severity, string> = {
  High: '▲',
  Medium: '◆',
  Low: '●',
  Informational: 'i',
};

/** compact keeps the label for screen readers only; the icon's shape still tells severities apart. */
export default function SeverityBadge({ severity, compact = false }: { severity: Severity; compact?: boolean }) {
  return (
    <span className={`badge badge-${severity.toLowerCase()}`}>
      <span className="badge-icon" aria-hidden="true">{ICONS[severity]}</span>
      <span className={compact ? 'sr-only' : undefined}>{severity}</span>
    </span>
  );
}
