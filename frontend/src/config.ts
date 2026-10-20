// Runtime settings. In the container, /config.js is written at startup from env vars (see
// docker-entrypoint.d/40-app-config.sh), so they can change without rebuilding the image.
declare global {
  interface Window {
    APP_CONFIG?: { grafanaUrl?: string };
  }
}

const FRONTEND_PORT = '8092';
const GRAFANA_PORT = '3011';

interface PageLocation {
  protocol: string;
  hostname: string;
}

/**
 * The configured Grafana URL if there is one; otherwise Grafana's host port on the host the
 * page was opened from. Port-forwarding services that put the port in the hostname (e.g.
 * Codespaces: name-8092.app.github.dev) get the same name with Grafana's port instead.
 */
export function resolveGrafanaUrl(configured: string | undefined, location: PageLocation): string {
  const url = configured?.trim();
  if (url) return url;
  const forwarded = location.hostname.replace(`-${FRONTEND_PORT}.`, `-${GRAFANA_PORT}.`);
  if (forwarded !== location.hostname) return `${location.protocol}//${forwarded}`;
  return `${location.protocol}//${location.hostname}:${GRAFANA_PORT}`;
}

export const GRAFANA_URL: string =
  typeof window === 'undefined' ? '' : resolveGrafanaUrl(window.APP_CONFIG?.grafanaUrl, window.location);
