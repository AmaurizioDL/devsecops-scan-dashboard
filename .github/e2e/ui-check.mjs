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

async function checkPdfLink(page, name, label) {
  const href = await page.getByRole('link', { name }).getAttribute('href');
  const res = await page.request.get(new URL(href, FRONTEND).toString());
  const magic = (await res.body()).subarray(0, 5).toString();
  check(`${label} downloads a PDF`, res.ok() && magic === '%PDF-', `${href} -> ${res.status()} ${magic}`);
}

async function checkOverflow(page, label) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (overflow > 0) report.warnings.push(`${label}: page scrolls horizontally by ${overflow}px`);
}

const browser = await chromium.launch();
try {
  // 1. Overview with the findings and the scan from the workflow's real scan.
  const desktop = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: 'light' });
  const page = await desktop.newPage();
  watch(page);
  await page.goto(FRONTEND);
  await page.locator('.kpi').first().waitFor({ timeout: 30_000 });
  await page.locator('.scan-row').first().waitFor({ timeout: 30_000 });
  const kpis = await page.locator('.kpi-grid').innerText();
  check('overview KPIs rendered', /Findings\s*\d+/.test(kpis), kpis.replace(/\s+/g, ' ').slice(0, 200));
  const owasp = await page.locator('section[aria-labelledby="summary-title"]').innerText();
  check('OWASP chart rendered', /A\d\d /.test(owasp), owasp.replace(/\s+/g, ' ').slice(0, 160));
  check('severity donut rendered', await page.locator('.donut-segment').count() > 0);
  const columns = await page.locator('.trend-col').count();
  check('new-findings-per-scan chart has a column per finished scan', columns >= 1, `${columns} columns`);
  await shot(page, '01-overview.png');

  // The backend's static trigger page derives its Grafana link the same way (port 3011).
  const expectedGrafana = process.env.EXPECTED_GRAFANA_URL;
  if (process.env.BACKEND) {
    const staticPage = await desktop.newPage();
    watch(staticPage);
    await staticPage.goto(process.env.BACKEND + '/');
    const staticHref = await staticPage.locator('#grafana-link').getAttribute('href');
    const expectedStatic = expectedGrafana ? `${expectedGrafana}/d/devsecops-scan-findings` : null;
    check('static page Grafana link', expectedStatic ? staticHref === expectedStatic : !!staticHref,
      `${staticHref ?? 'missing'}${expectedStatic ? ` (expected ${expectedStatic})` : ''}`);
    await staticPage.close();
  }

  // 2. Findings: filter by target, open one finding's detail panel.
  await page.locator('.nav').getByRole('link', { name: /^Findings/ }).click();
  await page.locator('.finding-row').first().waitFor({ timeout: 30_000 });
  check('findings table shows rows', true, await page.locator('.result-count').innerText());
  const target = page.getByLabel('Target');
  const targets = await target.locator('option').allInnerTexts();
  await target.selectOption({ index: 1 });
  const filtered = await page.locator('.result-count').innerText();
  const urls = await page.locator('.finding-row .url-cell').allInnerTexts();
  check('target filter keeps only that target', urls.every((u) => u === '—' || u.startsWith(targets[1])),
    `${targets[1]}: ${filtered}`);
  await page.locator('.finding-row .link-button').first().click();
  await page.locator('.drawer').waitFor({ timeout: 5_000 });
  await page.waitForTimeout(400); // slide-in animation
  const drawer = await page.locator('.drawer').innerText();
  check('finding detail panel opens', /OWASP category/.test(drawer), drawer.replace(/\s+/g, ' ').slice(0, 160));
  await shot(page, '02-finding-detail.png', false);
  await page.keyboard.press('Escape');
  check('detail panel closes with Escape', await page.locator('.drawer').count() === 0);

  // 3. Reports: links, scope, Grafana.
  await page.locator('.nav').getByRole('link', { name: /^Reports/ }).click();
  await checkPdfLink(page, /executive report/i, 'executive report link');
  await checkPdfLink(page, /technical report/i, 'technical report link');
  const grafanaHref = await page.getByRole('link', { name: /grafana/i }).getAttribute('href');
  check('Grafana link points to Grafana', expectedGrafana ? grafanaHref === expectedGrafana : !!grafanaHref,
    `${grafanaHref ?? 'missing'}${expectedGrafana ? ` (expected ${expectedGrafana})` : ''}`);
  await shot(page, '03-reports.png');

  // 4. Scan detail of the workflow's scan: its findings and per-scan PDFs.
  await page.locator('.nav').getByRole('link', { name: /^Scans/ }).click();
  // The newest scan that saved findings (a re-scan may find nothing new).
  await page.locator('.scan-row').filter({ has: page.locator('.new-count') }).first().locator('a').click();
  await page.locator('#scan-detail-title').waitFor({ timeout: 10_000 });
  const detailRows = await page.locator('.finding-row').first().waitFor({ timeout: 30_000 }).then(
    () => page.locator('.finding-row').count(), () => 0);
  check('scan detail lists the findings that scan found', detailRows > 0, `${detailRows} rows on the first page`);
  await checkPdfLink(page, /executive pdf/i, 'per-scan executive PDF');
  await checkPdfLink(page, /technical pdf/i, 'per-scan technical PDF');
  await shot(page, '04-scan-detail.png');

  // 5. Start a real scan from the UI (Low is preselected), follow it, reload, stop it.
  await page.getByRole('link', { name: /All scans/ }).click();
  await page.getByRole('button', { name: 'Start scan' }).click();
  await page.locator('.progress').waitFor({ timeout: 30_000 });
  check('scan started from the UI', true);
  // The history reloads right after the start response, so give that request a moment.
  const listed = await page.locator('.scan-row').first().filter({ hasText: 'Running' })
    .waitFor({ timeout: 10_000 }).then(() => true, () => false);
  const running = await page.locator('.scan-row').first().innerText();
  check('history lists the new scan as Running', listed, running.replace(/\s+/g, ' '));
  await page.waitForFunction(
    () => Number(document.querySelector('.progress')?.getAttribute('aria-valuenow') ?? 0) > 0,
    null,
    { timeout: 90_000 },
  ).catch(() => report.warnings.push('progress stayed at 0% for 90s'));
  const metrics = await page.locator('.scan-metrics').innerText();
  check('progress and ETA shown', /%/.test(metrics), metrics.replace(/\s+/g, ' '));
  await shot(page, '05-scan-in-progress.png', false);

  await page.reload();
  const resumed = await page.locator('.scan-status').waitFor({ timeout: 15_000 }).then(() => true, () => false);
  check('scan is followed again after a page reload', resumed);
  check('sidebar shows the running scan', await page.locator('.scan-pill').isVisible());
  await shot(page, '06-scan-after-reload.png', false);

  const stop = page.getByRole('button', { name: 'Stop scan' });
  if (await stop.isVisible()) {
    await stop.click();
  } else {
    report.warnings.push('scan finished before Stop could be clicked');
  }
  await page.locator('.phase-stopped, .phase-done').waitFor({ timeout: 120_000 });
  const finalStatus = await page.locator('.scan-status').innerText();
  check('scan reaches Stopped/Finished', true, finalStatus.replace(/\s+/g, ' '));
  await page.waitForTimeout(2_000); // the history refreshes after the scan ends
  const closed = await page.locator('.scan-row').first().innerText();
  check('history row is closed after the scan ends', /Stopped|Finished/.test(closed), closed.replace(/\s+/g, ' '));
  await shot(page, '07-scan-stopped.png');
  await checkOverflow(page, 'desktop');
  await desktop.close();

  // 6. Dark mode and phone width.
  const dark = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: 'dark' });
  const darkPage = await dark.newPage();
  watch(darkPage);
  await darkPage.goto(FRONTEND);
  await darkPage.locator('.kpi').first().waitFor({ timeout: 30_000 });
  const bg = await darkPage.evaluate(() => getComputedStyle(document.body).backgroundColor);
  check('dark mode follows the OS setting', bg === 'rgb(17, 17, 16)', bg);
  await shot(darkPage, '08-overview-dark.png');
  await dark.close();

  const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  const mobile = await phone.newPage();
  watch(mobile);
  await mobile.goto(FRONTEND);
  await mobile.locator('.kpi').first().waitFor({ timeout: 30_000 });
  await checkOverflow(mobile, '390px wide');
  await shot(mobile, '09-mobile.png');
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
