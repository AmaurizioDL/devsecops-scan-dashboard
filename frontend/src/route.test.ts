import { describe, expect, it } from 'vitest';
import { parseRoute, routeHref } from './route';

describe('hash routes', () => {
  it('parses views and the scan detail id, defaulting to the overview', () => {
    expect(parseRoute('')).toEqual({ view: 'overview' });
    expect(parseRoute('#/findings')).toEqual({ view: 'findings' });
    expect(parseRoute('#/scans/abc-123')).toEqual({ view: 'scans', scanId: 'abc-123' });
    expect(parseRoute('#/nope')).toEqual({ view: 'overview' });
  });

  it('round-trips through routeHref', () => {
    for (const route of [{ view: 'overview' as const }, { view: 'reports' as const }, { view: 'scans' as const, scanId: 'a b' }]) {
      expect(parseRoute(routeHref(route))).toEqual(route);
    }
  });
});
