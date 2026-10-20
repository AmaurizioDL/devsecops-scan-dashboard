#!/bin/sh
# Run by the nginx image's entrypoint before nginx starts: writes the frontend's runtime
# settings from env vars, so they can change without rebuilding the image.
# GRAFANA_URL empty/unset = the app derives it from the page URL (see src/config.ts).
set -eu

escaped=$(printf '%s' "${GRAFANA_URL:-}" | sed 's/\\/\\\\/g; s/"/\\"/g')
printf 'window.APP_CONFIG = { grafanaUrl: "%s" };\n' "$escaped" > /usr/share/nginx/html/config.js
echo "40-app-config.sh: GRAFANA_URL=${GRAFANA_URL:-<derived from page URL>}"
