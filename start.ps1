# Starts the whole stack (ZAP, Juice Shop, Postgres, backend, frontend, Grafana) with Docker,
# waits until it answers and opens the dashboard. Safe to run again: it only rebuilds what changed.
#
# Usage (Windows PowerShell):  powershell -ExecutionPolicy Bypass -File .\start.ps1
# Stop:                        docker compose down   (keeps findings; add -v to wipe them)
# 'Continue', not 'Stop': docker writes progress to stderr, which Windows PowerShell 5.1 would
# turn into a terminating error. Failures are caught through $LASTEXITCODE instead.
$ErrorActionPreference = 'Continue'
Set-Location -Path $PSScriptRoot

$UiUrl = 'http://localhost:8092'

function Fail($message) {
    Write-Host "ERROR: $message" -ForegroundColor Red
    exit 1
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail 'Docker is not installed. Get Docker Desktop: https://docs.docker.com/get-docker/'
}
cmd /c 'docker info >nul 2>&1'
if ($LASTEXITCODE -ne 0) { Fail 'Docker is installed but not running. Start Docker Desktop and run this again.' }
cmd /c 'docker compose version >nul 2>&1'
if ($LASTEXITCODE -ne 0) { Fail "Docker Compose v2 is required ('docker compose', not 'docker-compose')." }

# ZAP's active scan plus five other containers need room; below ~6 GB things get slow or killed.
$memGb = [math]::Floor([double](docker info --format '{{.MemTotal}}') / 1GB)
if ($memGb -lt 6) {
    Write-Host "WARNING: Docker has $memGb GB of memory. 8 GB is recommended; keep scans to the Low-risk rules." -ForegroundColor Yellow
}

if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
    Write-Host 'Created .env from .env.example (default local credentials).'
}

Write-Host 'Building and starting the stack. The first run downloads images and builds, about 5-10 minutes...'
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Fail 'docker compose up failed (see the output above).' }

function Wait-For($name, $url, $service) {
    Write-Host -NoNewline "Waiting for $name "
    for ($i = 0; $i -lt 120; $i++) {
        try {
            Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop | Out-Null
            Write-Host 'ready'
            return
        } catch {
            Write-Host -NoNewline '.'
            Start-Sleep -Seconds 5
        }
    }
    Write-Host ''
    Fail "$name did not answer at $url after 10 minutes. See: docker compose logs $service"
}

Wait-For 'the backend' 'http://localhost:8091/api/findings' 'backend'
Wait-For 'the dashboard' $UiUrl 'frontend'
Wait-For 'Juice Shop (the practice target)' 'http://localhost:3000' 'juice-shop'

Write-Host ''
Write-Host 'ScanDash is running.' -ForegroundColor Green
Write-Host "  Dashboard  $UiUrl        <- start here: Scans > Start scan (target http://juice-shop:3000)"
Write-Host '  Grafana    http://localhost:3011        (login admin / the GRAFANA_ADMIN_PASSWORD in .env)'
Write-Host '  API        http://localhost:8091/api/findings'
Write-Host ''
Write-Host "Stop it with: docker compose down   (findings are kept; 'docker compose down -v' wipes them)"

Start-Process $UiUrl
