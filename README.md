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

## Architecture

```
 ┌──────────────┐   HTTP (ZAP API, localhost:8080)   ┌──────────────┐
 │ Spring Boot  │ ───────────────────────────────────▶│  ZAP (zap)   │
 │ backend      │                                      │  Docker      │
 │ (host, :8081)│                                      │  container   │
 │ + static     │                                      └──────┬───────┘
 │   trigger    │                                             │ scans over
 │   page (/)   │                                             │ devsecops-net
 └──────┬───────┘                                      ┌───────▼──────┐
        │ JDBC (localhost:5432)                        │ juice-shop   │
        ▼                                               │ Docker       │
 ┌──────────────┐   Postgres datasource                │ container    │
 │ PostgreSQL 18 │◀──────────────────────────┐          └──────────────┘
 │ (native       │                            │
 │  Windows svc) │                    ┌───────┴──────┐
 └──────────────┘                    │ Grafana       │
                                      │ Docker        │
                                      │ container     │
                                      │ (:3001)       │
                                      └───────────────┘
```

- **ZAP** and **juice-shop** run in Docker, on a shared bridge network `devsecops-net`, so ZAP
  reaches the target by container name (e.g. `http://juice-shop:3000`).
- **PostgreSQL** runs natively on the host (not containerized) — the backend and Grafana both
  connect to it (backend over `localhost:5432`, Grafana over `host.docker.internal:5432` since
  it's inside Docker).
- The **Spring Boot backend** runs on the host too, and talks to ZAP's REST API on
  `localhost:8080` (exposed by the container). It also serves a static HTML page at `/` for
  triggering scans.
- **Grafana** runs in Docker, provisioned as code (`grafana/provisioning/`) — datasource and
  dashboard load automatically on `docker compose up`, no manual clicking required.

This split (native Postgres + host-run backend + containerized scan targets/engine/Grafana) is
a deliberate choice for this learning project, not an oversight.

## Prerequisites

- Docker Desktop
- Java 21 (JDK)
- Maven (or run/debug the `DashboardApplication` main class straight from IntelliJ, which
  bundles its own Maven — no separate install needed if you're using the IDE)
- PostgreSQL 18 installed natively (not in Docker)

## Setup

### 1. Provision PostgreSQL first

Create the database and table (adjust `psql` connection flags as needed for your local setup):

```bash
createdb -U postgres devsecops_dashboard
psql -U postgres -d devsecops_dashboard -f db/schema.sql
```

Grafana's datasource is provisioned against this database on container start, so it needs to
exist (even if empty) before step 2.

### 2. Configure credentials

Copy `.env.example` to `.env` and fill in your real Postgres password — `docker compose`
reads `.env` automatically to fill in `${DB_PASSWORD}` / `${GRAFANA_ADMIN_PASSWORD}` in
`docker-compose.yml` (used by the Grafana container):

```bash
cp .env.example .env
# edit .env: set DB_PASSWORD to your real local Postgres password
```

Spring Boot does **not** read `.env` — separately export the same values in your shell (or set
them in your IntelliJ Run Configuration) before running the backend in step 4:

```bash
# bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_local_postgres_password
```

```powershell
# PowerShell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your_local_postgres_password"
```

If unset, they default to `postgres` / `changeme`, which will fail auth against a real
instance — that's intentional, it forces you to set a real password rather than committing one.

### 3. Start ZAP + Juice Shop + Grafana

```bash
docker compose up -d
docker ps   # should show "zap", "juice-shop", and "grafana" as running
```

Sanity checks:

```bash
curl http://localhost:8080          # ZAP daemon responding
curl -I http://localhost:3000       # Juice Shop responding
curl -I http://localhost:3001       # Grafana responding
```

Open Grafana at **http://localhost:3001** (login `admin` / the `GRAFANA_ADMIN_PASSWORD` you
set, default `admin`). The "DevSecOps Scan Findings" dashboard and its Postgres datasource are
already provisioned — no manual setup needed. It'll be empty until you run a scan.

### 4. Run the backend

```bash
mvn spring-boot:run
```

or run `DashboardApplication` directly from IntelliJ (same environment variables need to be
set in the Run Configuration if you're not launching from a shell that already has them
exported).

The app starts on `http://localhost:8081`.

### 5. Run a scan

Open **http://localhost:8081** — the trigger page served by the backend. Enter a target URL
(e.g. `http://juice-shop:3000`), optionally check specific risk levels, and click "Run scan".

Or via curl:

```bash
curl -X POST "http://localhost:8081/api/scans?targetUrl=http://juice-shop:3000"
```

Note `targetUrl` uses the **container name** (`juice-shop`), not `localhost` — ZAP resolves
it via the `devsecops-net` Docker network, not from the host.

Optional: restrict the active scan to specific risk categories (see
`ZapScannerRiskCatalog`) to shorten scan time:

```bash
curl -X POST "http://localhost:8081/api/scans?targetUrl=http://juice-shop:3000&riskLevels=HIGH,MEDIUM"
```

This call is synchronous and blocks until the spider and active scan both complete — for a
full-coverage scan against Juice Shop this can take a while and is memory-intensive (see
Known limitations below).

### 6. View the results

- **Grafana** (http://localhost:3001) — the "DevSecOps Scan Findings" dashboard: severity
  breakdown donut, findings-over-time trend, stat cards, and a filterable table, with
  dashboard variables to filter by target/risk level/scan type.
- **Raw JSON**: `curl http://localhost:8081/api/findings`, or the "View raw findings" link on
  the trigger page.

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
  available memory quickly and slow the whole system down — this project was moved off a
  memory-constrained laptop for that reason. Run it on a machine with a reasonable amount of
  free RAM (8GB+ recommended for Docker Desktop + ZAP + Juice Shop + the JVM backend
  simultaneously), or scope scans down with `riskLevels` to reduce the number of active
  scanners running.
- The dashboard JSON (`grafana/provisioning/dashboards/json/scan-findings.json`) was authored
  by hand rather than exported from a running Grafana instance (this project is built/pushed
  from a machine that can't run the full stack — see above). Layout/queries are correct, but
  minor panel-schema quirks may need a small tweak the first time it's actually loaded.

## Project context

This is Project 1 of a 3-project, hands-on learning path for a QA → DevSecOps Engineer career
transition.
