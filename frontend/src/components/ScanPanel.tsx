import { useState } from 'react';
import { formatDuration } from '../findings';
import type { ScanPhase, ScanRiskLevel } from '../types';
import type { CurrentScan } from '../useCurrentScan';

const RISK_LEVELS: ScanRiskLevel[] = ['HIGH', 'MEDIUM', 'LOW'];

const PHASE_LABELS: Record<ScanPhase, string> = {
  SPIDER: 'Crawling (spider)',
  ACTIVE_SCAN: 'Active scan',
  DONE: 'Finished',
  STOPPED: 'Stopped',
  FAILED: 'Failed',
};

interface Props {
  scan: CurrentScan;
}

export default function ScanPanel({ scan }: Props) {
  // The backend resolves the target from inside the compose network, hence juice-shop:3000.
  const [targetUrl, setTargetUrl] = useState('http://juice-shop:3000');
  const [riskLevels, setRiskLevels] = useState<ScanRiskLevel[]>(['LOW']);
  const { run, active, busy, error } = scan;

  function toggleRisk(level: ScanRiskLevel) {
    setRiskLevels((current) =>
      current.includes(level) ? current.filter((l) => l !== level) : [...current, level],
    );
  }

  function handleStart(event: React.FormEvent) {
    event.preventDefault();
    void scan.start(targetUrl.trim(), riskLevels);
  }

  return (
    <section className="card" aria-labelledby="scan-title">
      <div className="card-header">
        <h2 id="scan-title">Run a scan</h2>
        <p className="muted">Spider + passive + active scan with OWASP ZAP. One scan runs at a time.</p>
      </div>
      <form onSubmit={handleStart} className="scan-form">
        <label className="field">
          <span>Target URL</span>
          <input
            type="url"
            required
            value={targetUrl}
            onChange={(e) => setTargetUrl(e.target.value)}
            disabled={active}
            placeholder="http://juice-shop:3000"
          />
        </label>
        <fieldset className="field" disabled={active}>
          <legend>Active-scan rules to run</legend>
          <div className="checkbox-row">
            {RISK_LEVELS.map((level) => (
              <label key={level} className="checkbox">
                <input type="checkbox" checked={riskLevels.includes(level)} onChange={() => toggleRisk(level)} />
                {level.charAt(0) + level.slice(1).toLowerCase()}-risk
              </label>
            ))}
          </div>
          <small className="muted">
            {riskLevels.length === 0
              ? 'None checked: every active-scan rule runs (can take 30+ minutes).'
              : 'Only the active-scan rules that test for the checked risk levels run (Low-risk alone takes about 2 minutes).'}{' '}
            Passive checks always run, so findings of any severity can appear.
          </small>
        </fieldset>
        <div className="button-row">
          <button type="submit" disabled={busy || active}>
            Start scan
          </button>
          {active && (
            <button type="button" className="danger" onClick={() => void scan.stop()} disabled={busy}>
              Stop scan
            </button>
          )}
        </div>
      </form>

      {error && (
        <div className="alert" role="alert">
          {error}
        </div>
      )}

      {run && (
        <div className="scan-status" aria-live="polite">
          <div className="scan-status-line">
            <span className={`phase phase-${run.phase.toLowerCase()}`}>{PHASE_LABELS[run.phase]}</span>
            <span className="muted truncate" title={run.targetUrl}>{run.targetUrl}</span>
          </div>
          {active && (
            <div
              className="progress"
              role="progressbar"
              aria-valuenow={run.percent}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Scan progress for the current phase"
            >
              <div className="progress-fill" style={{ width: `${run.percent}%` }} />
            </div>
          )}
          <dl className="scan-metrics">
            {active && (
              <>
                <div><dt>Phase progress</dt><dd>{run.percent}%</dd></div>
                <div><dt>ETA (phase)</dt><dd>{formatDuration(run.etaSeconds)}</dd></div>
              </>
            )}
            <div><dt>Elapsed</dt><dd>{formatDuration(run.elapsedSeconds)}</dd></div>
            {!active && run.findings && (
              <div><dt>New findings saved</dt><dd>{run.findings.length}</dd></div>
            )}
          </dl>
          {run.phase === 'FAILED' && run.errorMessage && (
            <div className="alert" role="alert">{run.errorMessage}</div>
          )}
        </div>
      )}
    </section>
  );
}
