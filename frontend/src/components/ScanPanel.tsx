import { useEffect, useRef, useState } from 'react';
import { ApiError, getScan, startScan, stopScan } from '../api';
import { formatDuration } from '../findings';
import type { ScanPhase, ScanRiskLevel, ScanRun } from '../types';

const POLL_INTERVAL_MS = 2000;
const STORAGE_KEY = 'devsecops.currentScanId';
const RISK_LEVELS: ScanRiskLevel[] = ['HIGH', 'MEDIUM', 'LOW'];

const PHASE_LABELS: Record<ScanPhase, string> = {
  SPIDER: 'Crawling (spider)',
  ACTIVE_SCAN: 'Active scan',
  DONE: 'Finished',
  STOPPED: 'Stopped',
  FAILED: 'Failed',
};

function isActive(run: ScanRun | null): boolean {
  return run?.phase === 'SPIDER' || run?.phase === 'ACTIVE_SCAN';
}

// localStorage can throw (private mode, blocked storage); the scan still works without it.
function readSavedScanId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function saveScanId(scanId: string | null) {
  try {
    if (scanId) localStorage.setItem(STORAGE_KEY, scanId);
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

interface Props {
  onScanFinished: () => void;
}

export default function ScanPanel({ onScanFinished }: Props) {
  // The backend resolves the target from inside the compose network, hence juice-shop:3000.
  const [targetUrl, setTargetUrl] = useState('http://juice-shop:3000');
  const [riskLevels, setRiskLevels] = useState<ScanRiskLevel[]>(['LOW']);
  const [run, setRun] = useState<ScanRun | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const onScanFinishedRef = useRef(onScanFinished);
  onScanFinishedRef.current = onScanFinished;

  // Resume following a scan started before a page reload. The backend has no
  // "current scan" endpoint, so the id is remembered in the browser.
  useEffect(() => {
    const savedId = readSavedScanId();
    if (!savedId) return;
    getScan(savedId)
      .then(setRun)
      .catch(() => saveScanId(null));
  }, []);

  const scanId = run?.scanId;
  const active = isActive(run);

  useEffect(() => {
    if (!scanId || !active) return;
    const timer = window.setInterval(async () => {
      try {
        const next = await getScan(scanId);
        setRun(next);
        if (!isActive(next)) {
          saveScanId(null);
          onScanFinishedRef.current();
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [scanId, active]);

  function toggleRisk(level: ScanRiskLevel) {
    setRiskLevels((current) =>
      current.includes(level) ? current.filter((l) => l !== level) : [...current, level],
    );
  }

  async function handleStart(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const started = await startScan(targetUrl.trim(), riskLevels);
      setRun(started);
      saveScanId(started.scanId);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setError('Another scan is already running. Wait for it to finish or stop it first.');
      } else {
        setError(e instanceof Error ? e.message : String(e));
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleStop() {
    if (!run) return;
    setBusy(true);
    try {
      setRun(await stopScan(run.scanId));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card" aria-labelledby="scan-title">
      <h2 id="scan-title">Run a scan</h2>
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
          <legend>Risk levels to scan for</legend>
          <div className="checkbox-row">
            {RISK_LEVELS.map((level) => (
              <label key={level} className="checkbox">
                <input type="checkbox" checked={riskLevels.includes(level)} onChange={() => toggleRisk(level)} />
                {level.charAt(0) + level.slice(1).toLowerCase()}
              </label>
            ))}
          </div>
          <small className="muted">
            {riskLevels.length === 0
              ? 'None selected: full scan (all rules, can take 30+ minutes).'
              : 'Only the scan rules for the selected levels run. Low alone takes about 2 minutes.'}
          </small>
        </fieldset>
        <div className="button-row">
          <button type="submit" disabled={busy || active}>
            Start scan
          </button>
          {active && (
            <button type="button" className="danger" onClick={handleStop} disabled={busy}>
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
