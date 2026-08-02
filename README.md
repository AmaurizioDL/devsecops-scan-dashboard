# DevSecOps Security Scan Dashboard

Backend service that drives [OWASP ZAP](https://www.zaproxy.org/) to run passive + active
security scans against a target web application, persists the findings to PostgreSQL, and
visualizes them in a [Grafana](https://grafana.com/) dashboard. Built as a hands-on learning
project (QA → DevSecOps transition) — it's usable against any site you are authorized to scan.

The frontend is intentionally **not** a custom SPA. DevSecOps job postings consistently ask
for Grafana/observability-dashboard fluency rather than React/Vue/Angular, and the panel
layout here (severity breakdown, findings-over-time trend, filterable table, stat cards)
mirrors the pattern used by real AppSec tools like DefectDojo, OWASP Dependency-Track, and
GitLab's Security Dashboard. A minimal static page (served by Spring Boot itself) covers the
one thing Grafana can't do: triggering a scan.

Everything runs in Docker — clone the repo, `docker compose up`, done. No native database
install, no host-level Java/Maven required.

## Architecture

```
                          devsecops-net (Docker bridge network)

 ┌──────────────┐   HTTP (ZAP API)      ┌──────────────┐
 │  backend      │ ─────────────────────▶│  zap         │
 │  (Spring Boot,│                        │  Docker      │
 │  container,   │                        │  container   │
 │  :8081)       │                        └──────┬───────┘
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
                                       │ (:3001)       │
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
- **grafana** runs in Docker, provisioned as code (`grafana/provisioning/`) — datasource and
  dashboard load automatically on `docker compose up`, no manual clicking required, and it
  reaches Postgres the same way the backend does: by container name on `devsecops-net`.

All five services are defined in a single `docker-compose.yml` — one command brings up the
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
docker ps   # should show "zap", "juice-shop", "postgres", "backend", and "grafana" running
```

`--build` is only needed the first time (or after changing backend source) — it builds the
backend image from the `Dockerfile`. Postgres's schema is applied automatically on first boot;
the backend waits for Postgres to report healthy before starting.

Sanity checks:

```bash
curl http://localhost:8080          # ZAP daemon responding
curl -I http://localhost:3000       # Juice Shop responding
curl -I http://localhost:8081       # Backend responding
curl -I http://localhost:3001       # Grafana responding
```

Open Grafana at **http://localhost:3001** (login `admin` / the `GRAFANA_ADMIN_PASSWORD` you
set, default `admin`). The "DevSecOps Scan Findings" dashboard and its Postgres datasource are
already provisioned — no manual setup needed. It'll be empty until you run a scan.

### 4. Run a scan

Open **http://localhost:8081** — the trigger page served by the backend. Enter a target URL
(e.g. `http://juice-shop:3000`), optionally check specific risk levels, and click "Run scan".

Or via curl:

```bash
curl -X POST "http://localhost:8081/api/scans?targetUrl=http://juice-shop:3000"
```

Note `targetUrl` uses the **container name** (`juice-shop`), not `localhost` — ZAP resolves
it via the `devsecops-net` Docker network, not from the host.

Optional: restrict the active scan to specific risk categories (see
`ZapScannerRiskCatalog`) to shorten scan time and reduce memory pressure:

```bash
curl -X POST "http://localhost:8081/api/scans?targetUrl=http://juice-shop:3000&riskLevels=HIGH,MEDIUM"
```

This call is synchronous and blocks until the spider and active scan both complete — for a
full-coverage scan against Juice Shop this can take a while and is memory-intensive (see
Known limitations below).

### 5. View the results

- **Grafana** (http://localhost:3001) — the "DevSecOps Scan Findings" dashboard: severity
  breakdown donut, findings-over-time trend, stat cards, and a filterable table, with
  dashboard variables to filter by target/risk level/scan type.
- **Raw JSON**: `curl http://localhost:8081/api/findings`, or the "View raw findings" link on
  the trigger page.

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

| Method | Path            | Description                                                             |
|--------|-----------------|--------------------------------------------------------------------------|
| POST   | `/api/scans`    | Runs spider + active scan against `targetUrl` (required query param), optionally scoped by `riskLevels` (comma-separated `HIGH,MEDIUM,LOW`). Returns the findings saved during this run. |
| GET    | `/api/findings` | Returns all stored findings, most recent first.                        |
| GET    | `/`             | Static HTML page to trigger a scan and link out to Grafana.            |

## Known limitations / deliberate scope cuts

- `POST /api/scans` is synchronous/blocking — running it as an async job (e.g. returning a
  scan id immediately and polling for status) is a documented future improvement.
- Endpoints return the `ScanFinding` JPA entity directly as JSON — introducing request/response
  DTOs is a documented future improvement.
- The ZAP active scan is CPU/memory-intensive. On low-resource machines it can consume
  available memory quickly and slow the whole system down. Run it on a machine with a
  reasonable amount of free RAM (8GB+ recommended for Docker Desktop + ZAP + Juice Shop + the
  backend container simultaneously), or scope scans down with `riskLevels` to reduce the
  number of active scanners running.
- `pgdata` and `grafana-data` are named Docker volumes — `docker compose down` keeps them,
  `docker compose down -v` deletes findings/dashboards state along with the containers.

## Project status

The full stack — `zap`, `juice-shop`, `postgres`, `backend`, and `grafana` — has been run
end-to-end via `docker compose up -d --build` on a dev machine: all five containers reach a
healthy/running state, the backend connects to the dockerized Postgres and applies/validates
the `scan_findings` schema on startup, the Grafana datasource connects to Postgres over the
shared `devsecops-net` network without any host-networking workarounds, and a scan run through
`/api/scans` produces rows visible in both `/api/findings` and the Grafana dashboard panels.

Full-coverage active scans remain memory-intensive (see Known limitations) — for routine local
verification, scoping with `riskLevels` is recommended over unscoped full scans.

## Project context

This is Project 1 of a 3-project, hands-on learning path for a QA → DevSecOps Engineer career
transition.
