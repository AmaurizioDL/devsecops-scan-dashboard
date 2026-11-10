import { routeHref, type Route, type View } from '../route';
import type { Theme } from '../theme';
import type { CurrentScan } from '../useCurrentScan';
import { FindingsIcon, MoonIcon, OverviewIcon, PlayIcon, ReportsIcon, ScansIcon, ShieldIcon, SunIcon } from './Icons';

const NAV: { view: View; label: string; Icon: typeof OverviewIcon }[] = [
  { view: 'overview', label: 'Overview', Icon: OverviewIcon },
  { view: 'findings', label: 'Findings', Icon: FindingsIcon },
  { view: 'scans', label: 'Scans', Icon: ScansIcon },
  { view: 'reports', label: 'Reports', Icon: ReportsIcon },
];

interface Props {
  route: Route;
  scan: CurrentScan;
  counts: Partial<Record<View, number>>;
  theme: Theme;
  onToggleTheme: () => void;
}

export default function Sidebar({ route, scan, counts, theme, onToggleTheme }: Props) {
  return (
    <aside className="sidebar">
      <a className="brand" href={routeHref({ view: 'overview' })}>
        <span className="brand-mark"><ShieldIcon size={20} /></span>
        <span className="brand-text">
          <strong>ScanDash</strong>
          <small>DevSecOps · OWASP ZAP</small>
        </span>
      </a>

      <nav className="nav" aria-label="Main">
        {NAV.map(({ view, label, Icon }) => (
          <a
            key={view}
            href={routeHref({ view })}
            className={`nav-link${route.view === view ? ' active' : ''}`}
            aria-current={route.view === view ? 'page' : undefined}
          >
            <Icon />
            <span>{label}</span>
            {counts[view] != null && <span className="nav-count">{counts[view]}</span>}
          </a>
        ))}
      </nav>

      <div className="sidebar-footer">
        {scan.active && scan.run ? (
          <a className="scan-pill" href={routeHref({ view: 'scans' })} title={scan.run.targetUrl}>
            <span className="scan-pill-line">
              <span className="pulse" aria-hidden="true" />
              Scanning · {scan.run.phase === 'SPIDER' ? 'spider' : 'active scan'} {scan.run.percent}%
            </span>
            <span className="mini-progress" aria-hidden="true">
              <span style={{ width: `${scan.run.percent}%` }} />
            </span>
          </a>
        ) : (
          <a className="button-link new-scan" href={routeHref({ view: 'scans' })}>
            <PlayIcon size={14} /> New scan
          </a>
        )}
        <button
          type="button"
          className="icon-button theme-toggle"
          onClick={onToggleTheme}
          aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          title={theme === 'dark' ? 'Light mode' : 'Dark mode'}
        >
          {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
        </button>
      </div>
    </aside>
  );
}
