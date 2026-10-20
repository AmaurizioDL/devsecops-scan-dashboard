// Drives the React frontend in headless Chromium against the real stack (used by
// e2e-verify.yml after a real scan has filled the database). Saves screenshots plus
// ui-report.json to OUT_DIR and exits non-zero if the UI breaks.
//
// Usage: PW_DIR=/tmp/pw FRONTEND=http://localhost:8092 OUT_DIR=out/ui node ui-check.mjs
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const { chromium } = createRequire(join(process.env.PW_DIR, 'node_modules') + '/')('playwright');
const FRONTEND = process.env.FRONTEND ?? 'http://localhost:8092';
const OUT_DIR = process.env.OUT_DIR ?? 'out/ui';
mkdirSync(OUT_DIR, { recursive: true });

const report = { checks: [], warnings: [], consoleErrors: [], pageErrors: [], failedRequests: [] };
const check = (name, ok, detail = '') => {
  report.checks.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ` (${detail})` : ''}`);
};

function watch(page) {
  page.on('console', (m) => m.type() === 'error' && report.consoleErrors.push(m.text()));
  page.on('pageerror', (e) => report.pageErrors.push(String(e)));
  page.on('response', (r) => {
    // 409 is the backend's expected answer when a scan is already running.
    if (r.url().includes('/api/') && r.status() >= 400 && r.status() !== 409) {
      report.failedRequests.push(`${r.status()} ${r.request().method()} ${r.url()}`);
    }
  });
}

const shot = (page, name, fullPage = true) => page.screenshot({ path: join(OUT_DIR, name), fullPage });

const browser = await chromium.launch();
try {
  // 1. Dashboard with the findings from the workflow's real scan.
  const desktop = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await desktop.newPage();
  watch(page);
  await page.goto(FRONTEND);
  await page.locator('.finding-row').first().waitFor({ timeout: 30_000 });
  check('findings table shows rows', true, await page.locator('.result-count').innerText());
  const summary = await page.locator('section[aria-labelledby="summary-title"]').innerText();
  check('summary panel rendered', summary.length > 20, summary.replace(/\s+/g, ' ').slice(0, 200));
  // With no GRAFANA_URL configured, the link is derived from the page URL (localhost:3011 here).
  const grafanaHref = await page.getByRole('link', { name: /grafana/i }).getAttribute('href');
  const expectedGrafana = process.env.EXPECTED_GRAFANA_URL;
  check('Grafana link points to Grafana', expectedGrafana ? grafanaHref === expectedGrafana : !!grafanaHref,
    `${grafanaHref ?? 'missing'}${expectedGrafana ? ` (expected ${expectedGrafana})` : ''}`);
  for (const name of [/executive/i, /technical/i]) {
    const href = await page.getByRole('link', { name }).getAttribute('href');
    const res = await page.request.get(new URL(href, FRONTEND).toString());
    const magic = (await res.body()).subarray(0, 5).toString();
    check(`report link ${name} downloads a PDF`, res.ok() && magic === '%PDF-', `${href} -> ${res.status()} ${magic}`);
  }
  await shot(page, '01-dashboard.png');

  // 2. Expand the first finding to see its real ZAP detail.
  await page.locator('.finding-row .link-button').first().click();
  await page.locator('.detail-row').first().waitFor({ timeout: 5_000 });
  check('finding row expands', true);
  await page.locator('.detail-row').first().scrollIntoViewIfNeeded();
  await shot(page, '02-finding-expanded.png', false);

  // 3. Start a real scan from the UI (Low is preselected), follow it, reload, stop it.
  await page.reload();
  await page.locator('.finding-row').first().waitFor({ timeout: 30_000 });
  await page.getByRole('button', { name: 'Start scan' }).click();
  await page.locator('.progress').waitFor({ timeout: 30_000 });
  check('scan started from the UI', true);
  await page.waitForFunction(
    () => Number(document.querySelector('.progress')?.getAttribute('aria-valuenow') ?? 0) > 0,
    null,
    { timeout: 90_000 },
  ).catch(() => report.warnings.push('progress stayed at 0% for 90s'));
  const metrics = await page.locator('.scan-metrics').innerText();
  check('progress and ETA shown', /%/.test(metrics), metrics.replace(/\s+/g, ' '));
  await page.locator('section[aria-labelledby="scan-title"]').scrollIntoViewIfNeeded();
  await shot(page, '03-scan-in-progress.png', false);

  await page.reload();
  const resumed = await page.locator('.scan-status').waitFor({ timeout: 15_000 }).then(() => true, () => false);
  check('scan is followed again after a page reload', resumed);
  await shot(page, '04-scan-after-reload.png', false);

  const stop = page.getByRole('button', { name: 'Stop scan' });
  if (await stop.isVisible()) {
    await stop.click();
  } else {
    report.warnings.push('scan finished before Stop could be clicked');
  }
  await page.locator('.phase-stopped, .phase-done').waitFor({ timeout: 120_000 });
  const finalStatus = await page.locator('.scan-status').innerText();
  check('scan reaches Stopped/Finished', true, finalStatus.replace(/\s+/g, ' '));
  await page.waitForTimeout(2_000); // the table refreshes after the scan ends
  await shot(page, '05-scan-stopped.png');
  await desktop.close();

  // 4. Phone width.
  const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  const mobile = await phone.newPage();
  watch(mobile);
  await mobile.goto(FRONTEND);
  await mobile.locator('.finding-row').first().waitFor({ timeout: 30_000 });
  const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (overflow > 0) report.warnings.push(`page scrolls horizontally by ${overflow}px at 390px wide`);
  await shot(mobile, '06-mobile.png');
  await phone.close();
} catch (e) {
  check('UI flow completed', false, String(e));
} finally {
  await browser.close();
}

check('no uncaught page errors', report.pageErrors.length === 0, report.pageErrors.join(' | '));
check('no failed API calls', report.failedRequests.length === 0, report.failedRequests.join(' | '));
writeFileSync(join(OUT_DIR, 'ui-report.json'), JSON.stringify(report, null, 2));
if (report.warnings.length) console.log(`WARNINGS:\n- ${report.warnings.join('\n- ')}`);
if (report.consoleErrors.length) console.log(`CONSOLE ERRORS:\n- ${report.consoleErrors.join('\n- ')}`);
process.exit(report.checks.every((c) => c.ok) ? 0 : 1);
