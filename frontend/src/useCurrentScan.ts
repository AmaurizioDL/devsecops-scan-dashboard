import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, fetchCurrentScan, getScan, startScan, stopScan } from './api';
import type { ScanRiskLevel, ScanRun } from './types';

const POLL_INTERVAL_MS = 2000;

export function isActive(run: ScanRun | null): boolean {
  return run?.phase === 'SPIDER' || run?.phase === 'ACTIVE_SCAN';
}

export interface CurrentScan {
  run: ScanRun | null;
  active: boolean;
  busy: boolean;
  error: string | null;
  start: (targetUrl: string, riskLevels: ScanRiskLevel[]) => Promise<void>;
  stop: () => Promise<void>;
}

/**
 * Follows the single scan the backend runs at a time; onScanChanged fires when one starts or
 * ends. Lives at the app level so the sidebar and every view see the same progress, and asks
 * the backend for the running scan on load, so a page reload (or another browser) picks it up.
 */
export function useCurrentScan(onScanChanged: () => void): CurrentScan {
  const [run, setRun] = useState<ScanRun | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const onScanChangedRef = useRef(onScanChanged);
  onScanChangedRef.current = onScanChanged;

  useEffect(() => {
    fetchCurrentScan()
      .then((current) => current && setRun(current))
      .catch(() => {
        // no running scan to resume; the dashboard itself still works
      });
  }, []);

  const scanId = run?.scanId;
  const active = isActive(run);

  // Fires on every start and end, wherever the new state came from: a poll, the start
  // response, or a stop response that already reports the scan as stopped.
  const wasActive = useRef(active);
  useEffect(() => {
    if (wasActive.current !== active) onScanChangedRef.current();
    wasActive.current = active;
  }, [active]);

  useEffect(() => {
    if (!scanId || !active) return;
    const timer = window.setInterval(async () => {
      try {
        const next = await getScan(scanId);
        setRun(next);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [scanId, active]);

  const start = useCallback(async (targetUrl: string, riskLevels: ScanRiskLevel[]) => {
    setBusy(true);
    setError(null);
    try {
      setRun(await startScan(targetUrl, riskLevels));
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setError('Another scan is already running. Wait for it to finish or stop it first.');
      } else {
        setError(e instanceof Error ? e.message : String(e));
      }
    } finally {
      setBusy(false);
    }
  }, []);

  const stop = useCallback(async () => {
    if (!scanId) return;
    setBusy(true);
    try {
      setRun(await stopScan(scanId));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }, [scanId]);

  return { run, active, busy, error, start, stop };
}
