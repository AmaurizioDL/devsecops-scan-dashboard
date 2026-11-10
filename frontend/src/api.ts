import type { Finding, ScanRecord, ScanRiskLevel, ScanRun } from './types';

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  if (!response.ok) {
    // Spring's default error body is JSON with a "message" field; fall back to the status text.
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = await response.json();
      if (body?.message) message = body.message;
    } catch {
      // body was not JSON, keep the status text
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return null as T;
  return response.json() as Promise<T>;
}

/** All findings, or only the ones a given scan found for the first time. */
export function fetchFindings(scanId?: string): Promise<Finding[]> {
  const query = scanId ? `?${new URLSearchParams({ scanId })}` : '';
  return request<Finding[]>(`/api/findings${query}`);
}

export function fetchScans(): Promise<ScanRecord[]> {
  return request<ScanRecord[]>('/api/scans');
}

/** The scan running right now, or null when none is. */
export function fetchCurrentScan(): Promise<ScanRun | null> {
  return request<ScanRun | null>('/api/scans/current');
}

export function startScan(targetUrl: string, riskLevels: ScanRiskLevel[]): Promise<ScanRun> {
  const params = new URLSearchParams({ targetUrl });
  if (riskLevels.length > 0) params.set('riskLevels', riskLevels.join(','));
  return request<ScanRun>(`/api/scans?${params}`, { method: 'POST' });
}

export function getScan(scanId: string): Promise<ScanRun> {
  return request<ScanRun>(`/api/scans/${encodeURIComponent(scanId)}`);
}

export function stopScan(scanId: string): Promise<ScanRun> {
  return request<ScanRun>(`/api/scans/${encodeURIComponent(scanId)}/stop`, { method: 'POST' });
}

export type ReportKind = 'executive' | 'technical';

/** Which findings a report covers: everything, one target, or what one scan found. */
export interface ReportScope {
  targetUrl?: string;
  scanId?: string;
}

export function reportUrl(kind: ReportKind, scope: ReportScope = {}): string {
  const params = new URLSearchParams();
  if (scope.scanId) params.set('scanId', scope.scanId);
  else if (scope.targetUrl) params.set('targetUrl', scope.targetUrl);
  const query = params.toString();
  return `/api/reports/${kind}${query ? `?${query}` : ''}`;
}
