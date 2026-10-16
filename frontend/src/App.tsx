import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchFindings } from './api';
import { summarize } from './findings';
import type { Finding } from './types';
import ScanPanel from './components/ScanPanel';
import SummaryPanel from './components/SummaryPanel';
import FindingsTable from './components/FindingsTable';
import ReportsPanel from './components/ReportsPanel';

export default function App() {
  const [findings, setFindings] = useState<Finding[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadFindings = useCallback(async () => {
    setLoading(true);
    try {
      setFindings(await fetchFindings());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadFindings();
  }, [loadFindings]);

  const summary = useMemo(() => summarize(findings), [findings]);

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>DevSecOps Scan Dashboard</h1>
          <p className="muted">OWASP ZAP scans, findings mapped to the OWASP Top 10, and downloadable reports.</p>
        </div>
        <button type="button" className="secondary" onClick={() => void loadFindings()} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh findings'}
        </button>
      </header>

      {error && (
        <div className="alert" role="alert">
          Could not load findings: {error}. Is the backend running?
        </div>
      )}

      <div className="top-grid">
        <ScanPanel onScanFinished={() => void loadFindings()} />
        <ReportsPanel hasFindings={findings.length > 0} />
      </div>
      <SummaryPanel summary={summary} />
      <FindingsTable findings={findings} loading={loading} />
    </div>
  );
}
