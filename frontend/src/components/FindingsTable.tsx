import { Fragment, useMemo, useState } from 'react';
import {
  filterFindings,
  normalizeSeverity,
  owaspKey,
  owaspLabel,
  SEVERITIES,
  type FindingFilters,
  type Severity,
} from '../findings';
import type { Finding } from '../types';
import SeverityBadge from './SeverityBadge';

const PAGE_SIZE = 25;

interface Props {
  findings: Finding[];
  loading: boolean;
}

export default function FindingsTable({ findings, loading }: Props) {
  const [filters, setFilters] = useState<FindingFilters>({ severity: '', owasp: '', search: '' });
  const [page, setPage] = useState(0);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const owaspOptions = useMemo(
    () => [...new Set(findings.map(owaspKey))].sort(),
    [findings],
  );
  const filtered = useMemo(() => filterFindings(findings, filters), [findings, filters]);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const rows = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  function updateFilters(patch: Partial<FindingFilters>) {
    setFilters((current) => ({ ...current, ...patch }));
    setPage(0);
  }

  return (
    <section className="card" aria-labelledby="findings-title">
      <h2 id="findings-title">Findings</h2>
      <div className="filter-row">
        <label className="field">
          <span>Search</span>
          <input
            type="search"
            value={filters.search}
            onChange={(e) => updateFilters({ search: e.target.value })}
            placeholder="Alert name, URL or CWE-79"
          />
        </label>
        <label className="field">
          <span>Severity</span>
          <select
            value={filters.severity}
            onChange={(e) => updateFilters({ severity: e.target.value as Severity | '' })}
          >
            <option value="">All</option>
            {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
        <label className="field">
          <span>OWASP category</span>
          <select value={filters.owasp} onChange={(e) => updateFilters({ owasp: e.target.value })}>
            <option value="">All</option>
            {owaspOptions.map((key) => <option key={key} value={key}>{owaspLabel(key)}</option>)}
          </select>
        </label>
      </div>

      <p className="muted result-count">
        {loading ? 'Loading findings…' : `${filtered.length} of ${findings.length} findings`}
      </p>

      <div className="table-scroll">
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col">Severity</th>
              <th scope="col">Alert</th>
              <th scope="col">OWASP</th>
              <th scope="col">CWE</th>
              <th scope="col">Affected URL</th>
              <th scope="col">Confidence</th>
              <th scope="col">Detected</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((f) => {
              const expanded = expandedId === f.id;
              return (
                <Fragment key={f.id}>
                  <tr className="finding-row">
                    <td><SeverityBadge severity={normalizeSeverity(f.riskLevel)} /></td>
                    <td>
                      <button
                        type="button"
                        className="link-button"
                        aria-expanded={expanded}
                        onClick={() => setExpandedId(expanded ? null : f.id)}
                      >
                        {f.alertName}
                      </button>
                    </td>
                    <td>{owaspKey(f) === 'Unmapped' ? '—' : owaspKey(f).slice(0, 3)}</td>
                    <td className="nowrap">{f.cweId != null ? `CWE-${f.cweId}` : '—'}</td>
                    <td className="url-cell" title={f.affectedUrl ?? ''}>{f.affectedUrl ?? '—'}</td>
                    <td>{f.confidence ?? '—'}</td>
                    <td className="nowrap">{f.detectedAt ? new Date(f.detectedAt).toLocaleString() : '—'}</td>
                  </tr>
                  {expanded && (
                    <tr className="detail-row">
                      <td colSpan={7}>
                        <dl className="detail-list">
                          <dt>OWASP category</dt><dd>{owaspLabel(owaspKey(f))}</dd>
                          <dt>Target</dt><dd>{f.targetUrl}</dd>
                          {f.description && <><dt>Description</dt><dd>{f.description}</dd></>}
                          {f.solution && <><dt>Remediation</dt><dd>{f.solution}</dd></>}
                          {f.evidence && <><dt>Evidence</dt><dd><code>{f.evidence}</code></dd></>}
                          {f.reference && <><dt>References</dt><dd className="pre-line">{f.reference}</dd></>}
                        </dl>
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
            {!loading && rows.length === 0 && (
              <tr><td colSpan={7} className="muted empty">No findings match these filters.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {pageCount > 1 && (
        <nav className="pagination" aria-label="Findings pages">
          <button type="button" className="secondary" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>
            Previous
          </button>
          <span className="muted">Page {currentPage + 1} of {pageCount}</span>
          <button type="button" className="secondary" disabled={currentPage >= pageCount - 1} onClick={() => setPage(currentPage + 1)}>
            Next
          </button>
        </nav>
      )}
    </section>
  );
}
