import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchFindings, fetchScans } from './api';
import { summarize } from './findings';
import { useRoute, type View } from './route';
import { useTheme } from './theme';
import type { Finding, ScanRecord } from './types';
import { useCurrentScan } from './useCurrentScan';
import FindingsTable from './components/FindingsTable';
import { RefreshIcon } from './components/Icons';
import Overview from './components/Overview';
import Reports from './components/Reports';
import ScanDetail from './components/ScanDetail';
import ScanHistoryTable from './components/ScanHistoryTable';
import ScanPanel from './components/ScanPanel';
import Sidebar from './components/Sidebar';

const TITLES: Record<View, { title: string; subtitle: string }> = {
  overview: { title: 'Security overview', subtitle: 'OWASP ZAP findings mapped to the OWASP Top 10, across every scan.' },
  findings: { title: 'Findings', subtitle: 'Every stored finding. Click one for the full ZAP detail and the fix.' },
  scans: { title: 'Scans', subtitle: 'Start a scan, follow its progress and browse the scan history.' },
  reports: { title: 'Reports', subtitle: 'Executive and technical PDF reports for any scope.' },
};

export default function App() {
  const route = useRoute();
  const [theme, toggleTheme] = useTheme();
  const [findings, setFindings] = useState<Finding[]>([]);
  const [history, setHistory] = useState<ScanRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [nextFindings, nextHistory] = await Promise.all([fetchFindings(), fetchScans()]);
      setFindings(nextFindings);
      setHistory(nextHistory);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const scan = useCurrentScan(() => void loadData());
  const summary = useMemo(() => summarize(findings), [findings]);
  const { title, subtitle } = TITLES[route.view];

  return (
    <div className="app">
      <Sidebar
        route={route}
        scan={scan}
        counts={{ findings: findings.length, scans: history.length }}
        theme={theme}
        onToggleTheme={toggleTheme}
      />
      <main className="main">
        <header className="topbar">
          <div>
            <h1>{title}</h1>
            <p className="muted">{subtitle}</p>
          </div>
          <button type="button" className="secondary" onClick={() => void loadData()} disabled={loading}>
            <RefreshIcon size={14} /> {loading ? 'Loading…' : 'Refresh'}
          </button>
        </header>

        {error && (
          <div className="alert" role="alert">
            Could not load data: {error}. Is the backend running?
          </div>
        )}

        {route.view === 'overview' && <Overview summary={summary} history={history} />}
        {route.view === 'findings' && <FindingsTable findings={findings} history={history} loading={loading} />}
        {route.view === 'scans' && (
          route.scanId ? (
            <ScanDetail scanId={route.scanId} history={history} historyLoading={loading} />
          ) : (
            <div className="stack">
              <ScanPanel scan={scan} />
              <section className="card" aria-labelledby="history-title">
                <div className="card-header">
                  <h2 id="history-title">Scan history</h2>
                  <p className="muted">{history.length} scans</p>
                </div>
                <ScanHistoryTable history={history} />
              </section>
            </div>
          )
        )}
        {route.view === 'reports' && <Reports findings={findings} history={history} />}
      </main>
    </div>
  );
}
