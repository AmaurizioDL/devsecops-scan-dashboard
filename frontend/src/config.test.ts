import { describe, expect, it } from 'vitest';
import { resolveGrafanaUrl } from './config';

describe('resolveGrafanaUrl', () => {
  it('uses the configured URL when there is one', () => {
    expect(resolveGrafanaUrl(' https://grafana.example.com ', { protocol: 'http:', hostname: 'localhost' }))
      .toBe('https://grafana.example.com');
  });

  it('defaults to port 3011 on the host the page was opened from', () => {
    expect(resolveGrafanaUrl(undefined, { protocol: 'http:', hostname: 'localhost' })).toBe('http://localhost:3011');
    expect(resolveGrafanaUrl('', { protocol: 'http:', hostname: '192.168.1.20' })).toBe('http://192.168.1.20:3011');
  });

  it('swaps the port inside forwarded hostnames such as Codespaces', () => {
    expect(resolveGrafanaUrl(undefined, { protocol: 'https:', hostname: 'fluffy-space-8092.app.github.dev' }))
      .toBe('https://fluffy-space-3011.app.github.dev');
  });
});
