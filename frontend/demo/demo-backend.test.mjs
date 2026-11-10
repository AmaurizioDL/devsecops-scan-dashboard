import { describe, expect, it } from 'vitest';
import { createDemoState, demoPdf, isRunning, scanView, startScan } from './demo-backend.mjs';

const NOW = Date.parse('2026-11-10T12:00:00');

describe('demo backend', () => {
  it('seeds a history whose counts match the findings linked to each scan', () => {
    const state = createDemoState(NOW);
    expect(state.scans.length).toBeGreaterThan(3);
    for (const scan of state.scans) {
      const linked = state.findings.filter((f) => f.scanRunId === scan.id).length;
      expect(scan.newHigh + scan.newMedium + scan.newLow + scan.newInformational).toBe(linked);
    }
  });

  it('runs a simulated scan through its phases, saves new findings and rejects a second scan', () => {
    const state = createDemoState(NOW);
    const before = state.findings.length;
    const started = startScan(state, 'http://juice-shop:3000', 'LOW', NOW);
    expect(started.phase).toBe('SPIDER');
    expect(startScan(state, 'http://other', '', NOW + 1000)).toBeNull();
    expect(scanView(state, NOW + 10_000).phase).toBe('ACTIVE_SCAN');

    const done = scanView(state, NOW + 30_000);
    expect(done.phase).toBe('DONE');
    expect(done.findings.length).toBeGreaterThan(0);
    expect(state.findings.length).toBe(before + done.findings.length);
    expect(state.scans[0]).toMatchObject({ id: started.scanId, status: 'DONE' });
    expect(isRunning(state, NOW + 31_000)).toBe(false);
  });

  it('stops a scan on request and keeps what it found', () => {
    const state = createDemoState(NOW);
    startScan(state, 'http://juice-shop:3000', 'LOW', NOW);
    state.current.stopRequested = true;
    const stopped = scanView(state, NOW + 3000);
    expect(stopped.phase).toBe('STOPPED');
    expect(state.scans[0].status).toBe('STOPPED');
    expect(stopped.findings).toHaveLength(1);
  });

  it('serves a real (if tiny) PDF for the report links', () => {
    const pdf = demoPdf().toString('latin1');
    expect(pdf.startsWith('%PDF-')).toBe(true);
    expect(pdf.trimEnd().endsWith('%%EOF')).toBe(true);
  });
});
