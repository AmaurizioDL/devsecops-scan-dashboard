import { describe, expect, it } from 'vitest';
import { newFindingsTotal, riskLevelsLabel, scanDurationSeconds, scanNumber, trendScans } from './scans';
import type { ScanRecord } from './types';

function scan(overrides: Partial<ScanRecord>): ScanRecord {
  return {
    id: 'id', targetUrl: 'http://juice-shop:3000', riskLevels: 'LOW', status: 'DONE',
    startedAt: '2026-11-09T10:00:00', finishedAt: '2026-11-09T10:02:05', errorMessage: null,
    newHigh: 0, newMedium: 0, newLow: 0, newInformational: 0, ...overrides,
  };
}

describe('scan history helpers', () => {
  it('measures duration to the finish, or to now while running', () => {
    expect(scanDurationSeconds(scan({}))).toBe(125);
    expect(scanDurationSeconds(scan({ finishedAt: null }), new Date('2026-11-09T10:00:30'))).toBe(30);
  });

  it('labels the active-scan rules that ran', () => {
    expect(riskLevelsLabel(scan({ riskLevels: 'HIGH,LOW' }))).toBe('High, Low');
    expect(riskLevelsLabel(scan({ riskLevels: '' }))).toBe('All rules');
    expect(riskLevelsLabel(scan({ riskLevels: null }))).toBe('All rules');
  });

  it('adds up new findings across severities', () => {
    expect(newFindingsTotal(scan({ newHigh: 1, newMedium: 2, newLow: 3, newInformational: 4 }))).toBe(10);
  });

  it('numbers scans from the oldest and charts the latest finished ones oldest first', () => {
    const history = [
      scan({ id: 'c', startedAt: '2026-11-09T12:00:00', status: 'RUNNING', finishedAt: null }),
      scan({ id: 'b', startedAt: '2026-11-09T11:00:00' }),
      scan({ id: 'a', startedAt: '2026-11-09T10:00:00', status: 'FAILED' }),
    ];
    expect(scanNumber(history[0], history)).toBe(3);
    expect(trendScans(history, 10).map((s) => s.id)).toEqual(['a', 'b']);
    expect(trendScans(history, 1).map((s) => s.id)).toEqual(['b']);
  });
});
