// Mirrors the backend's JSON (ScanFinding and ScanRecord entities, ScanRunView record).

export interface Finding {
  id: number;
  targetUrl: string;
  scanType: string;
  alertName: string;
  riskLevel: string;
  cweId: number | null;
  owaspCategory: string | null;
  affectedUrl: string | null;
  description: string | null;
  solution: string | null;
  confidence: string | null;
  pluginId: string | null;
  evidence: string | null;
  reference: string | null;
  detectedAt: string | null;
  /** Scan that first found it; null for findings stored before scan history existed. */
  scanRunId: string | null;
}

export type ScanPhase = 'SPIDER' | 'ACTIVE_SCAN' | 'DONE' | 'STOPPED' | 'FAILED';

export type ScanRiskLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export interface ScanRun {
  scanId: string;
  targetUrl: string;
  phase: ScanPhase;
  percent: number;
  etaSeconds: number | null;
  elapsedSeconds: number;
  findings: Finding[] | null;
  errorMessage: string | null;
}

export type ScanStatus = 'RUNNING' | 'DONE' | 'STOPPED' | 'FAILED';

/** One row of the scan history. */
export interface ScanRecord {
  id: string;
  targetUrl: string;
  /** Comma-separated ScanRiskLevel values; empty = every active-scan rule ran. */
  riskLevels: string | null;
  status: ScanStatus;
  startedAt: string;
  finishedAt: string | null;
  errorMessage: string | null;
  newHigh: number;
  newMedium: number;
  newLow: number;
  newInformational: number;
}
