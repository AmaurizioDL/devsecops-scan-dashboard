# DevSecOps Security Scan Dashboard

Backend service that drives [OWASP ZAP](https://www.zaproxy.org/) to run passive + active
security scans against a target web application, persists the findings to PostgreSQL, and
visualizes them in a [Grafana](https://grafana.com/) dashboard. Built as a hands-on learning
project (QA → DevSecOps transition) — it's usable against any site you are authorized to scan.

The main UI is a **React + Vite single-page app** (`frontend/`, served by nginx on
http://localhost:8092): start a scan and follow its progress/ETA, browse and filter findings,
see a severity and OWASP Top 10 summary, and download the executive and technical PDF reports.
Grafana stays alongside it for historical, observability-style dashboards (severity breakdown,
findings-over-time trend, stat cards), the panel layout used by AppSec tools like DefectDojo,
OWASP Dependency-Track, and GitLab's Security Dashboard.

Everything runs in Docker — clone the repo, `docker compose up`, done. No native database
install, no host-level Java/Maven required.

## Cloud Development (GitHub Codespaces)

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/AmaurizioDL/devsecops-scan-dashboard)

The full stack — ZAP especially — is CPU/memory-heavy (see [Known limitations](#known-limitations--deliberate-scope-cuts)).
Running it alongside an IDE on a modest laptop can saturate the machine. Codespaces runs the
same `docker-compose.yml` on a cloud VM instead of your machine, and it's also how anyone
without Docker installed locally can try this project — click the badge, no local setup at all.

The `.devcontainer/` config starts only `app` (a Maven + shell container with the repo mounted
live) and `postgres` by default, so the Codespace itself stays light. Bring up the rest only
when you need them:

```bash
# inside the Codespace terminal
mvn spring-boot:run                                   # iterate on the backend directly
# in another terminal, when you want to actually scan/see the dashboard:
docker compose up -d zap juice-shop grafana
```

Codespaces forwards ports 8091 (backend), 3011 (Grafana), 3000 (Juice Shop), 8090 (ZAP), and
5432 (Postgres) automatically — same URLs/flow as the [Setup](#setup) section below, just
opened via the forwarded-ports tab instead of `localhost`. Free personal GitHub accounts include
a monthly quota of Codespaces core-hours/storage; check your usage under
**GitHub → Settings → Codespaces** if you keep one running a while.

## Architecture

```
                          devsecops-net (Docker bridge network)

 ┌──────────────┐   HTTP (ZAP API)      ┌──────────────┐
 │  backend      │ ─────────────────────▶│  zap         │
 │  (Spring Boot,│                        │  Docker      │
 │  container,   │                        │  container   │
 │  :8091)       │                        └──────┬───────┘
 │  + static     │                               │ scans over
 │  trigger page │                               │ devsecops-net
 │  (/)          │                        ┌───────▼──────┐
 └──────┬────────┘                        │ juice-shop   │
        │ JDBC (postgres:5432)            │ Docker       │
        ▼                                 │ container    │
 ┌──────────────┐   Postgres datasource   └──────────────┘
 │ postgres      │◀──────────────────────────┐
 │ Docker        │                            │
 │ container     │                    ┌───────┴──────┐
 └──────────────┘                     │ grafana       │
                                       │ Docker        │
                                       │ container     │
                                       │ (:3011)       │
                                       └───────────────┘
```

- **zap** and **juice-shop** run in Docker on a shared bridge network `devsecops-net`, so ZAP
  reaches the target by container name (e.g. `http://juice-shop:3000`).
- **postgres** runs in Docker too (official `postgres` image), with its schema
  (`db/schema.sql`) auto-applied on first start via the Postgres image's
  `/docker-entrypoint-initdb.d/` convention, and its data persisted in a named volume
  (`pgdata`) so it survives `docker compose down` (but not `down -v`).
- The **backend** (Spring Boot) is built and run from its own `Dockerfile`, and reaches
  Postgres and ZAP by container name (`postgres:5432`, `http://zap:8080`) over
  `devsecops-net`. It also serves a static HTML page at `/` for triggering scans.
- **frontend** is the React + Vite app, built in a multi-stage `frontend/Dockerfile` and
  served by nginx on `:8092`. nginx also proxies `/api/*` to `backend:8081`, so the browser
  talks to a single origin and the backend needs no CORS configuration.
- **grafana** runs in Docker, provisioned as code (`grafana/provisioning/`) — datasource and
  dashboard load automatically on `docker compose up`, no manual clicking required, and it
  reaches Postgres the same way the backend does: by container name on `devsecops-net`.

All six services are defined in a single `docker-compose.yml` — one command brings up the
whole stack on any machine with Docker installed.

## Prerequisites

- Docker Desktop (or Docker Engine + Compose v2) — that's it.
- Java 21 + Maven are only needed if you want to run the backend *outside* Docker (e.g. from
  IntelliJ) against the dockerized Postgres/ZAP — see "Alternative: run the backend outside
  Docker" below.

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/AmaurizioDL/devsecops-scan-dashboard.git
cd devsecops-scan-dashboard
```

### 2. Configure credentials

Copy `.env.example` to `.env`. The defaults work out of the box for local use (they're read by
every service in `docker-compose.yml`, including Postgres itself, so backend/Grafana/Postgres
always agree on the same credentials):

```bash
cp .env.example .env
# optional: edit .env to set your own DB_PASSWORD / GRAFANA_ADMIN_PASSWORD
```

### 3. Start the full stack

```bash
docker compose up -d --build
docker ps   # should show "zap", "juice-shop", "postgres", "backend", "frontend", and "grafana" running
```

`--build` is only needed the first time (or after changing backend source) — it builds the
backend image from the `Dockerfile`. Postgres's schema is applied automatically on first boot;
the backend waits for Postgres to report healthy before starting.

Sanity checks:

```bash
curl http://localhost:8090          # ZAP daemon responding
curl -I http://localhost:3000       # Juice Shop responding
curl -I http://localhost:8091       # Backend responding
curl -I http://localhost:3011       # Grafana responding
curl -I http://localhost:8092       # Frontend responding
```

Open Grafana at **http://localhost:3011** (login `admin` / the `GRAFANA_ADMIN_PASSWORD` you
set, default `admin`). The "DevSecOps Scan Findings" dashboard and its Postgres datasource are
already provisioned — no manual setup needed. It'll be empty until you run a scan.

### 4. Run a scan

Open **http://localhost:8092**, the React frontend. Enter a target URL (e.g.
`http://juice-shop:3000`), optionally check specific risk levels, and click "Start scan".
The scan runs in the background: the app polls scan status every 2s and shows the phase
(spider, then active scan), a progress bar, an ETA, and a "Stop scan" button to cancel it
mid-run. Reloading the page keeps following the same scan. When it finishes, the findings
table and summary refresh on their own. (The older static trigger page is still served by the
backend at http://localhost:8091.)

Or via curl. `POST /api/scans` returns immediately with a `scanId`:

```bash
curl -X POST "http://localhost:8091/api/scans?targetUrl=http://juice-shop:3000"
# {"scanId":"...", "phase":"SPIDER", "percent":0, ...}

curl "http://localhost:8091/api/scans/<scanId>"
# poll this — phase moves SPIDER -> ACTIVE_SCAN -> DONE, percent/etaSeconds update each call

curl -X POST "http://localhost:8091/api/scans/<scanId>/stop"
# cancels the running scan; whatever findings existed at that point are still saved
```

Note `targetUrl` uses the **container name** (`juice-shop`), not `localhost` — ZAP resolves
it via the `devsecops-net` Docker network, not from the host.

Optional: restrict the active scan to specific risk categories (see
`ZapScannerRiskCatalog`) to shorten scan time and reduce memory pressure:

```bash
curl -X POST "http://localhost:8091/api/scans?targetUrl=http://juice-shop:3000&riskLevels=HIGH,MEDIUM"
```

Only one scan runs at a time (matches how ZAP itself works) — a second `POST /api/scans` while
one is in progress gets `409 Conflict`. A full-coverage scan against Juice Shop can still take a
while and is memory-intensive (see Known limitations below); `riskLevels` or the stop button are
the ways to cut that short.

### 5. View the results

- **Frontend** (http://localhost:8092): stat cards (total findings, unique vulnerabilities,
  counts by severity), a bar chart of findings by OWASP Top 10 category (also viewable as a
  table), and a findings table with search (alert, URL, `CWE-79`), severity and OWASP filters,
  and expandable rows with description, remediation, evidence and references.
- **Grafana** (http://localhost:3011) — the "DevSecOps Scan Findings" dashboard: severity
  breakdown donut, findings-over-time trend, stat cards, and a filterable table, with
  dashboard variables to filter by target/risk level/scan type.
- **Raw JSON**: `curl http://localhost:8091/api/findings`, or the "View raw findings" link on
  the trigger page.
- **PDF reports** (buttons in the frontend, links on the trigger page and in the Grafana dashboard), structured after the
  executive/technical split used by the OWASP WSTG reporting guide, PTES and NIST SP 800-115:
  - **Executive summary** (`/api/reports/executive`): overall risk posture, scope and
    limitations, counts by severity and OWASP Top 10 category, top 5 risks in business terms,
    strategic recommendations, and a remediation roadmap by timeframe (P1 0-7 days, P2 30 days,
    P3 90 days).
  - **Technical report** (`/api/reports/technical`): a findings summary table plus one entry per
    *unique* vulnerability (VULN-001, ...) with risk, ZAP confidence, CWE, OWASP category,
    affected URLs, example evidence, remediation and references, and appendices on methodology
    and rating scales.

  Raw ZAP alerts (one per rule per URL, repeated across scans) are consolidated by ZAP rule +
  risk level, so a missing header on 40 pages is one vulnerability with 40 affected URLs, not 40
  findings. Both accept an optional `?targetUrl=` filter.

Panels auto-refresh every 10s and query Postgres directly with no caching layer — a finding
saved mid-scan shows up on the next refresh tick. If the Grafana tab was in the background
(browsers throttle timers on inactive tabs) or you're checking right after a scan finishes,
give it a few seconds or reload the page rather than assuming the dashboard is stale.

### Alternative: run the backend outside Docker

If you're actively developing the backend and want faster iteration than rebuilding the
image each time, you can leave `zap`, `juice-shop`, `postgres`, and `grafana` in Docker
(`docker compose up -d zap juice-shop postgres grafana`) and run the backend from your host
instead:

```bash
export DB_USERNAME=postgres DB_PASSWORD=changeme   # match your .env
mvn spring-boot:run
```

This works unmodified because `application.properties` defaults `DB_HOST`/`ZAP_BASE_URL` to
`localhost`, and Postgres/ZAP both publish their ports to the host in `docker-compose.yml`.

## API

| Method | Path                       | Description                                                             |
|--------|----------------------------|--------------------------------------------------------------------------|
| POST   | `/api/scans`               | Starts a spider + active scan against `targetUrl` (required query param) in the background, optionally scoped by `riskLevels` (comma-separated `HIGH,MEDIUM,LOW`). Returns `202` with the initial scan status immediately; `409` if a scan is already running. |
| GET    | `/api/scans/{scanId}`      | Current status of a scan: `phase` (`SPIDER`/`ACTIVE_SCAN`/`DONE`/`STOPPED`/`FAILED`), `percent`, `etaSeconds` (nullable), `elapsedSeconds`, and `findings` (once `DONE`/`STOPPED`). `404` once a newer scan has replaced it — only the current/last scan is tracked. |
| POST   | `/api/scans/{scanId}/stop` | Requests cancellation of a running scan. Findings already collected at that point are still saved (phase becomes `STOPPED`). `404` if unknown. |
| GET    | `/api/findings`            | Returns all stored findings, most recent first.                        |
| GET    | `/api/reports/executive`   | Executive summary PDF (optional `targetUrl` filter).                   |
| GET    | `/api/reports/technical`   | Technical vulnerability report PDF (optional `targetUrl` filter). `/api/reports/pdf` is kept as an alias. |
| GET    | `/`                        | Static HTML page to trigger a scan and link out to Grafana.            |

## Tests

Unit tests (JUnit 5 + Mockito + AssertJ, no Docker/database required) cover the business logic
that matters most for correctness: the ZAP alert → `ScanFinding` mapping (including CWE-id edge
cases: null/negative/non-numeric values), passive-vs-active dedup by alert id, the scan-timeout
guard, `targetUrl`/`riskLevels` request validation in `ScanController`, the risk-level →
ZAP-scanner-id catalog, the sliding-window ETA calculation (`ScanProgressEstimator`), the
single-current-scan conflict rule (`ScanRunRegistry`), stopping a scan mid-spider or
mid-active-scan (partial findings still saved, ZAP's stop action called), the report
consolidation/ordering rules (`VulnerabilityConsolidator`), and that both PDF reports render
valid PDFs from realistic ZAP text.

```bash
mvn test
```

Frontend unit tests (Vitest) cover the data logic behind the UI: severity normalization,
OWASP category mapping, the summary counts and unique-vulnerability grouping (same rule as the
PDF reports), and the table's filtering/sorting.

```bash
cd frontend
npm ci
npm test
npm run dev   # dev server on http://localhost:5173, proxies /api to the backend on :8091
```

The `Dockerfile` also runs `mvn package` (not `-DskipTests`), so `docker compose up --build`
won't produce a `backend` image if a test fails.

## Known limitations / deliberate scope cuts

- Only one scan runs at a time — there's a single tracked "current scan" slot, not a job queue.
  This matches how ZAP itself and the project's usage pattern already work; a second
  `POST /api/scans` while one is running gets `409 Conflict` instead of being queued.
- Scan progress/state is in-memory only (`ScanRunRegistry`), not persisted. If the backend
  restarts mid-scan, tracking is lost — ZAP itself keeps running the orphaned scan in that edge
  case. Reconciling on startup is out of scope for now.
- The ETA shown during a scan is a short-term extrapolation (recent %/second, via
  `ScanProgressEstimator`), not a historical average per target — it can swing early in a
  phase before enough samples accumulate ("calculando…" until then), and phase 2 (active scan)
  restarts its own estimate from 0 rather than blending with phase 1's rate.
- Endpoints return the `ScanFinding` JPA entity directly as JSON — introducing request/response
  DTOs for the older endpoints is a documented future improvement (`ScanRunView`, used by the
  newer scan-status endpoints, is the project's first DTO).
- The ZAP active scan is CPU/memory-intensive. On low-resource machines it can consume
  available memory quickly and slow the whole system down. Run it on a machine with a
  reasonable amount of free RAM (8GB+ recommended for Docker Desktop + ZAP + Juice Shop + the
  backend container simultaneously), or scope scans down with `riskLevels` to reduce the
  number of active scanners running.
- `pgdata` and `grafana-data` are named Docker volumes — `docker compose down` keeps them,
  `docker compose down -v` deletes findings/dashboards state along with the containers.
- Upgrading an existing database: `db/schema.sql` only runs automatically on a fresh `pgdata`
  volume. If your volume predates the report columns (`confidence`, `plugin_id`, `evidence`,
  `reference`), the backend will refuse to start (`ddl-auto=validate`). Either re-run the schema
  file, which adds them idempotently without deleting findings
  (`docker exec -i postgres psql -U <DB_USERNAME> -d devsecops_dashboard < db/schema.sql`), or
  reset with `docker compose down -v`. Findings stored before the upgrade show confidence
  "Unknown" and are grouped by alert name instead of ZAP rule id.
- Report risk and confidence are ZAP's own ratings; no CVSS score or OWASP Risk Rating is
  computed.
- The OWASP Top 10:2021 category comes from the `OWASP_2021_Axx` tag that each ZAP rule declares;
  the project's CWE → category table is only a fallback for alerts without that tag. Findings
  stored before this change keep the category computed at scan time (CWE only) until the
  target is scanned again.

## Project status

**Complete.** The full stack (`zap`, `juice-shop`, `postgres`, `backend`, `grafana`, and the
React `frontend`) runs end-to-end from a single `docker compose up -d --build` on a clean clone.
All six containers reach a healthy/running state. The backend validates the `scan_findings`
schema against the dockerized Postgres on startup. Grafana connects to Postgres over the shared
`devsecops-net` network, and the frontend reaches the backend through its own nginx `/api`
proxy, so neither needs CORS or host-networking workarounds.

Nothing depends on the machine it's built on: no native Postgres/Java/Maven/Node install and no
machine-specific credentials are required. `git clone` → `cp .env.example .env` →
`docker compose up -d --build` is the entire setup, on any machine with Docker.

**What's in place:**

- **Scanning:** async, risk-scoped ZAP scans (spider + active scan) with per-phase progress, ETA
  and stop, driven from the frontend at `http://localhost:8092` or directly through the API.
- **Results:** findings in the frontend (summary by severity and OWASP Top 10 category, and a
  filterable/searchable table) and in the provisioned Grafana dashboard.
- **Reports:** an executive PDF and a technical PDF. Both consolidate raw ZAP alerts into unique
  vulnerabilities mapped to OWASP Top 10:2021.

**How it's verified:**

- 55 backend unit tests (JUnit 5 + Mockito) run as a gate in the `Dockerfile` build, plus 6
  frontend Vitest tests (see [Tests](#tests)).
- The `e2e-verify.yml` GitHub Actions workflow (manual `workflow_dispatch`) brings up the whole
  stack on a GitHub-hosted runner and scans Juice Shop. It then checks the stored findings,
  downloads both PDF reports, queries Grafana's datasource and panels, and confirms the
  frontend serves the app and proxies `/api`. Run it with
  `gh workflow run e2e-verify.yml --ref master`.

Full-coverage active scans remain memory-intensive (see Known limitations), so scoping with
`riskLevels` is recommended over unscoped full scans for routine verification. What remains
beyond that is the documented, deliberate scope cuts above. They are not bugs, just
intentionally out of scope for this learning project.

## Project context

This is Project 1 of a 3-project, hands-on learning path for a QA → DevSecOps Engineer career
transition.
