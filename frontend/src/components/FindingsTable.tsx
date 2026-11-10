import { useCallback, useMemo, useState } from 'react';
import {
  filterFindings,
  NO_FILTERS,
  normalizeSeverity,
  owaspKey,
  owaspLabel,
  SEVERITIES,
  targetsOf,
  type FindingFilters,
  type Severity,
} from '../findings';
import { formatDateTime } from '../scans';
import type { Finding, ScanRecord } from '../types';
import FindingDrawer from './FindingDrawer';
import SeverityBadge from './SeverityBadge';

const PAGE_SIZE = 25;

interface Props {
  findings: Finding[];
  history: ScanRecord[];
  loading: boolean;
}

export default function FindingsTable({ findings, history, loading }: Props) {
  const [filters, setFilters] = useState<FindingFilters>(NO_FILTERS);
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const owaspOptions = useMemo(() => [...new Set(findings.map(owaspKey))].sort(), [findings]);
  const targetOptions = useMemo(() => targetsOf(findings), [findings]);
  const filtered = useMemo(() => filterFindings(findings, filters), [findings, filters]);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const rows = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);
  const selected = selectedId != null ? findings.find((f) => f.id === selectedId) : undefined;
  const filtering = JSON.stringify(filters) !== JSON.stringify(NO_FILTERS);
  // Stable, so the drawer's focus/Escape effect runs once per opened finding.
  const closeDrawer = useCallback(() => setSelectedId(null), []);

  function updateFilters(patch: Partial<FindingFilters>) {
    setFilters((current) => ({ ...current, ...patch }));
    setPage(0);
  }

  return (
    <section className="card" aria-labelledby="findings-title">
      <div className="card-header">
        <h2 id="findings-title">Findings</h2>
        <p className="muted result-count">
          {loading ? 'Loading findings…' : `${filtered.length} of ${findings.length} findings`}
        </p>
      </div>
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
          <span>Target</span>
          <select value={filters.target} onChange={(e) => updateFilters({ target: e.target.value })}>
            <option value="">All targets</option>
            {targetOptions.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
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
      {filtering && (
        <button type="button" className="link-button clear-filters" onClick={() => updateFilters(NO_FILTERS)}>
          Clear filters
        </button>
      )}

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
              <th scope="col">First seen</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((f) => (
              <tr
                key={f.id}
                className={`finding-row${selectedId === f.id ? ' selected' : ''}`}
                onClick={() => setSelectedId(f.id)}
              >
                <td><SeverityBadge severity={normalizeSeverity(f.riskLevel)} /></td>
                <td>
                  <button
                    type="button"
                    className="link-button"
                    aria-haspopup="dialog"
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedId(f.id);
                    }}
                  >
                    {f.alertName}
                  </button>
                </td>
                <td>{owaspKey(f) === 'Unmapped' ? '—' : owaspKey(f).slice(0, 3)}</td>
                <td className="nowrap">{f.cweId != null ? `CWE-${f.cweId}` : '—'}</td>
                <td className="url-cell" title={f.affectedUrl ?? ''}>{f.affectedUrl ?? '—'}</td>
                <td>{f.confidence ?? '—'}</td>
                <td className="nowrap">{formatDateTime(f.detectedAt)}</td>
              </tr>
            ))}
            {!loading && rows.length === 0 && (
              <tr>
                <td colSpan={7} className="muted empty">
                  {findings.length === 0 ? 'No findings yet. Run a scan to fill this table.' : 'No findings match these filters.'}
                </td>
              </tr>
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

      {selected && <FindingDrawer finding={selected} history={history} onClose={closeDrawer} />}
    </section>
  );
}
