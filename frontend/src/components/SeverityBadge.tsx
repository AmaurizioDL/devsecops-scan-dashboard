import type { Severity } from '../findings';

// Severity is a status, so it always ships with an icon and a text label, never color alone.
const ICONS: Record<Severity, string> = {
  High: '▲',
  Medium: '◆',
  Low: '●',
  Informational: 'i',
};

export default function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <span className={`badge badge-${severity.toLowerCase()}`}>
      <span className="badge-icon" aria-hidden="true">{ICONS[severity]}</span>
      {severity}
    </span>
  );
}
