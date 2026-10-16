// Mirrors the backend's JSON (ScanFinding entity and ScanRunView record).

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
