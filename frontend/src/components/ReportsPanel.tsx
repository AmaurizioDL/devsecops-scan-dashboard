import { GRAFANA_URL, REPORT_URLS } from '../api';

interface Props {
  hasFindings: boolean;
}

export default function ReportsPanel({ hasFindings }: Props) {
  return (
    <section className="card" aria-labelledby="reports-title">
      <h2 id="reports-title">Reports</h2>
      <ul className="report-list">
        <li>
          <a className="button-link" href={REPORT_URLS.executive} download>
            Executive report (PDF)
          </a>
          <small className="muted">Two to three pages for management: risk posture, top risks, remediation roadmap.</small>
        </li>
        <li>
          <a className="button-link" href={REPORT_URLS.technical} download>
            Technical report (PDF)
          </a>
          <small className="muted">One entry per vulnerability, with CWE, OWASP category, affected URLs and the fix.</small>
        </li>
        <li>
          <a className="button-link secondary" href={GRAFANA_URL} target="_blank" rel="noreferrer">
            Open Grafana ↗
          </a>
          <small className="muted">Historical dashboards over the same findings database.</small>
        </li>
      </ul>
      {!hasFindings && <p className="muted">No findings yet: the reports will be empty until you run a scan.</p>}
    </section>
  );
}
