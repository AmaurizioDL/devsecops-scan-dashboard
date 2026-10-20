import type { Finding, ScanRiskLevel, ScanRun } from './types';

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
  return response.json() as Promise<T>;
}

export function fetchFindings(): Promise<Finding[]> {
  return request<Finding[]>('/api/findings');
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

export const REPORT_URLS = {
  executive: '/api/reports/executive',
  technical: '/api/reports/technical',
};
