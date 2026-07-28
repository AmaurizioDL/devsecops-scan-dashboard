# DevSecOps Security Scan Dashboard

Backend service that drives [OWASP ZAP](https://www.zaproxy.org/) to run passive + active
security scans against a target web application, and persists the findings to PostgreSQL
for later querying. Built as a hands-on learning project (QA → DevSecOps transition) — it's
usable against any site you are authorized to scan.

## Architecture

```
 ┌──────────────┐   HTTP (ZAP API, localhost:8080)   ┌──────────────┐
 │ Spring Boot  │ ───────────────────────────────────▶│  ZAP (zap)   │
 │ backend      │                                      │  Docker      │
 │ (host, :8081)│                                      │  container   │
 └──────┬───────┘                                      └──────┬───────┘
        │ JDBC (localhost:5432)                                │ scans over
        ▼                                                      │ devsecops-net
 ┌──────────────┐                                      ┌───────▼──────┐
 │ PostgreSQL 18 │                                      │ juice-shop   │
 │ (native       │                                      │ Docker       │
 │  Windows svc) │                                      │ container    │
 └──────────────┘                                      └──────────────┘
```

- **ZAP** and **juice-shop** run in Docker, on a shared bridge network `devsecops-net`, so ZAP
  reaches the target by container name (e.g. `http://juice-shop:3000`).
- **PostgreSQL** runs natively on the host (not containerized) — the backend connects to it
  over `localhost:5432`.
- The **Spring Boot backend** runs on the host too, and talks to ZAP's REST API on
  `localhost:8080` (exposed by the container).

This split (native Postgres + host-run backend + containerized scan targets/engine) is a
deliberate choice for this learning project, not an oversight.

## Prerequisites

- Docker Desktop
- Java 21 (JDK)
- Maven (or run/debug the `DashboardApplication` main class straight from IntelliJ, which
  bundles its own Maven — no separate install needed if you're using the IDE)
- PostgreSQL 18 installed natively (not in Docker)

## Setup

### 1. Start ZAP + Juice Shop

```bash
docker compose up -d
docker ps   # should show "zap" and "juice-shop" as healthy/running
```

Sanity checks:

```bash
curl http://localhost:8080          # ZAP daemon responding
curl -I http://localhost:3000       # Juice Shop responding
```

### 2. Provision PostgreSQL

Create the database and table (adjust `psql` connection flags as needed for your local setup):

```bash
createdb -U postgres devsecops_dashboard
psql -U postgres -d devsecops_dashboard -f db/schema.sql
```

### 3. Configure credentials

The backend reads DB credentials from environment variables (never hardcode them in
`application.properties` — see `.env.example`):

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

### 4. Run the backend

```bash
mvn spring-boot:run
```

or run `DashboardApplication` directly from IntelliJ (same environment variables need to be
set in the Run Configuration if you're not launching from a shell that already has them
exported).

The app starts on `http://localhost:8081`.

### 5. Run a scan

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

### 6. Read stored findings

```bash
curl http://localhost:8081/api/findings
```

## API

| Method | Path            | Description                                                             |
|--------|-----------------|--------------------------------------------------------------------------|
| POST   | `/api/scans`    | Runs spider + active scan against `targetUrl` (required query param), optionally scoped by `riskLevels` (comma-separated `HIGH,MEDIUM,LOW`). Returns the findings saved during this run. |
| GET    | `/api/findings` | Returns all stored findings, most recent first.                        |

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

## Project context

This is Project 1 of a 3-project, hands-on learning path for a QA → DevSecOps Engineer career
transition.
