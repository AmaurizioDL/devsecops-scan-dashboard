import { useEffect, useState } from 'react';

// Hash routes (#/findings, #/scans/<id>) keep every view linkable without a router
// dependency or any nginx change: the server always serves the same index.html.
export type View = 'overview' | 'findings' | 'scans' | 'reports';

export interface Route {
  view: View;
  /** Set on #/scans/<id>: the scan whose detail is shown. */
  scanId?: string;
}

const VIEWS: View[] = ['overview', 'findings', 'scans', 'reports'];

export function parseRoute(hash: string): Route {
  const [first, second] = hash.replace(/^#\/?/, '').split('/');
  const view = VIEWS.find((v) => v === first) ?? 'overview';
  if (view === 'scans' && second) return { view, scanId: decodeURIComponent(second) };
  return { view };
}

export function routeHref(route: Route): string {
  if (route.view === 'scans' && route.scanId) return `#/scans/${encodeURIComponent(route.scanId)}`;
  return route.view === 'overview' ? '#/' : `#/${route.view}`;
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => parseRoute(window.location.hash));
  useEffect(() => {
    const onChange = () => {
      setRoute(parseRoute(window.location.hash));
      window.scrollTo(0, 0);
    };
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}
