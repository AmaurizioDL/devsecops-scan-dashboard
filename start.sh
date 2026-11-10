#!/usr/bin/env bash
# Starts the whole stack (ZAP, Juice Shop, Postgres, backend, frontend, Grafana) with Docker,
# waits until it answers and opens the dashboard. Safe to run again: it only rebuilds what changed.
#
# Usage: ./start.sh            (macOS, Linux, or Git Bash on Windows)
# Stop:  docker compose down   (keeps findings; add -v to wipe them)
set -euo pipefail
cd "$(dirname "$0")"

UI_URL=http://localhost:8092

fail() { echo "ERROR: $*" >&2; exit 1; }

command -v docker > /dev/null || fail "Docker is not installed. Get Docker Desktop: https://docs.docker.com/get-docker/"
docker info > /dev/null 2>&1 || fail "Docker is installed but not running. Start Docker Desktop and run this again."
docker compose version > /dev/null 2>&1 || fail "Docker Compose v2 is required ('docker compose', not 'docker-compose')."
command -v curl > /dev/null || fail "curl is required to check when the stack is ready."

# ZAP's active scan plus five other containers need room; below ~6 GB things get slow or killed.
MEM_GB=$(( $(docker info --format '{{.MemTotal}}') / 1024 / 1024 / 1024 ))
if [ "$MEM_GB" -lt 6 ]; then
  echo "WARNING: Docker has ${MEM_GB} GB of memory. 8 GB is recommended; keep scans to the Low-risk rules."
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example (default local credentials)."
fi

echo "Building and starting the stack. The first run downloads images and builds, about 5-10 minutes..."
docker compose up -d --build

wait_for() {
  local name=$1 url=$2 service=$3
  printf 'Waiting for %s ' "$name"
  for _ in $(seq 1 120); do
    if curl -fsS -o /dev/null "$url"; then echo "ready"; return 0; fi
    printf '.'
    sleep 5
  done
  echo
  fail "$name did not answer at $url after 10 minutes. See: docker compose logs $service"
}

wait_for "the backend" http://localhost:8091/api/findings backend
wait_for "the dashboard" "$UI_URL" frontend
wait_for "Juice Shop (the practice target)" http://localhost:3000 juice-shop

cat << EOF

ScanDash is running.
  Dashboard  $UI_URL        <- start here: Scans > Start scan (target http://juice-shop:3000)
  Grafana    http://localhost:3011        (login admin / the GRAFANA_ADMIN_PASSWORD in .env)
  API        http://localhost:8091/api/findings

Stop it with: docker compose down   (findings are kept; 'docker compose down -v' wipes them)
EOF

if command -v open > /dev/null; then open "$UI_URL"
elif command -v xdg-open > /dev/null; then xdg-open "$UI_URL" > /dev/null 2>&1 &
elif command -v cmd.exe > /dev/null; then cmd.exe /c start "" "$UI_URL"
fi
