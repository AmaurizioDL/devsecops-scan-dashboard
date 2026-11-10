import { useState } from 'react';
import { SEVERITIES, type Severity } from '../findings';
import SeverityBadge from './SeverityBadge';

const RADIUS = 40;
const STROKE = 14;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const GAP = 1.5; // surface gap between segments, in viewBox units

interface Props {
  counts: Record<Severity, number>;
}

/** Severity mix as a ring; the legend doubles as the exact-value table. */
export default function SeverityDonut({ counts }: Props) {
  const [hovered, setHovered] = useState<Severity | null>(null);
  const total = SEVERITIES.reduce((sum, s) => sum + counts[s], 0);
  const present = SEVERITIES.filter((s) => counts[s] > 0);
  const gap = present.length > 1 ? GAP : 0;

  let offset = 0;
  const segments = present.map((severity) => {
    const length = (counts[severity] / total) * CIRCUMFERENCE;
    const segment = { severity, dash: Math.max(length - gap, 0.5), offset };
    offset += length;
    return segment;
  });

  const focus = hovered && counts[hovered] > 0 ? hovered : null;

  return (
    <div className="donut-layout">
      <div className="donut">
        <svg viewBox="0 0 100 100" role="img"
          aria-label={`Findings by severity: ${SEVERITIES.map((s) => `${s} ${counts[s]}`).join(', ')}`}>
          <circle cx="50" cy="50" r={RADIUS} fill="none" stroke="var(--surface-2)" strokeWidth={STROKE} />
          <g transform="rotate(-90 50 50)">
            {segments.map(({ severity, dash, offset: start }) => (
              <circle
                key={severity}
                cx="50" cy="50" r={RADIUS} fill="none"
                stroke={`var(--sev-${severity.toLowerCase()})`}
                strokeWidth={STROKE}
                strokeDasharray={`${dash} ${CIRCUMFERENCE - dash}`}
                strokeDashoffset={-start}
                className={`donut-segment${focus && focus !== severity ? ' dimmed' : ''}`}
                onMouseEnter={() => setHovered(severity)}
                onMouseLeave={() => setHovered(null)}
              />
            ))}
          </g>
        </svg>
        <div className="donut-center" aria-hidden="true">
          <span className="donut-value">{focus ? counts[focus] : total}</span>
          <span className="donut-label">{focus ?? 'findings'}</span>
        </div>
      </div>

      <ul className="donut-legend">
        {SEVERITIES.map((severity) => (
          <li
            key={severity}
            tabIndex={0}
            className={focus && focus !== severity ? 'dimmed' : ''}
            onMouseEnter={() => setHovered(severity)}
            onMouseLeave={() => setHovered(null)}
            onFocus={() => setHovered(severity)}
            onBlur={() => setHovered(null)}
          >
            <SeverityBadge severity={severity} />
            <span className="legend-value">{counts[severity]}</span>
            <span className="legend-share muted">
              {total > 0 ? `${Math.round((counts[severity] / total) * 100)}%` : '—'}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
