import { useEffect, useRef } from 'react';
import { normalizeSeverity, owaspKey, owaspLabel } from '../findings';
import { routeHref } from '../route';
import { formatDateTime, scanNumber } from '../scans';
import type { Finding, ScanRecord } from '../types';
import { CloseIcon } from './Icons';
import SeverityBadge from './SeverityBadge';

interface Props {
  finding: Finding;
  history: ScanRecord[];
  onClose: () => void;
}

/** Side panel with one finding's full ZAP detail; Escape or the backdrop closes it. */
export default function FindingDrawer({ finding: f, history, onClose }: Props) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const scan = f.scanRunId ? history.find((s) => s.id === f.scanRunId) : undefined;

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} aria-hidden="true" />
      <aside className="drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title">
        <header className="drawer-header">
          <div>
            <SeverityBadge severity={normalizeSeverity(f.riskLevel)} />
            <h2 id="drawer-title">{f.alertName}</h2>
          </div>
          <button ref={closeRef} type="button" className="icon-button" onClick={onClose} aria-label="Close details">
            <CloseIcon />
          </button>
        </header>

        <dl className="detail-list">
          <dt>OWASP category</dt><dd>{owaspLabel(owaspKey(f))}</dd>
          <dt>CWE</dt><dd>{f.cweId != null ? `CWE-${f.cweId}` : '—'}</dd>
          <dt>Confidence</dt><dd>{f.confidence ?? '—'}</dd>
          <dt>Target</dt><dd>{f.targetUrl}</dd>
          <dt>Affected URL</dt><dd>{f.affectedUrl ?? '—'}</dd>
          <dt>Found by</dt>
          <dd>
            {f.scanType === 'ACTIVE' ? 'Active scan' : 'Passive scan'}
            {scan && <> · <a href={routeHref({ view: 'scans', scanId: scan.id })} onClick={onClose}>scan #{scanNumber(scan, history)}</a></>}
          </dd>
          <dt>First seen</dt><dd>{formatDateTime(f.detectedAt)}</dd>
        </dl>

        {f.description && <section className="drawer-section"><h3>Description</h3><p>{f.description}</p></section>}
        {f.solution && <section className="drawer-section"><h3>Remediation</h3><p>{f.solution}</p></section>}
        {f.evidence && <section className="drawer-section"><h3>Evidence</h3><pre className="evidence">{f.evidence}</pre></section>}
        {f.reference && <section className="drawer-section"><h3>References</h3><p className="pre-line">{f.reference}</p></section>}
      </aside>
    </>
  );
}
