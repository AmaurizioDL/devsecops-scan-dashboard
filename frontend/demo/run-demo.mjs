// `npm run demo`: the real frontend on Vite's dev server, backed by the in-memory demo backend,
// so the UI can be tried without Docker. Opens the browser; Ctrl+C stops both.
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { startDemoBackend } from './demo-backend.mjs';

const FRONTEND_PORT = Number(process.env.DEMO_PORT ?? 5180);
const BACKEND_PORT = Number(process.env.DEMO_BACKEND_PORT ?? 8099);
const root = fileURLToPath(new URL('..', import.meta.url));

const backend = await startDemoBackend(BACKEND_PORT);
// Read by vite.config.ts (API proxy target) and by the app (shows the demo banner).
process.env.BACKEND_URL = `http://127.0.0.1:${BACKEND_PORT}`;
process.env.VITE_DEMO = 'true';

const vite = await createServer({
  root,
  server: { port: FRONTEND_PORT, strictPort: false, open: process.env.DEMO_NO_OPEN ? false : '/' },
});
await vite.listen();
console.log('\nScanDash demo is running (sample data, simulated scans, no Docker needed).');
vite.printUrls();
console.log('  Press Ctrl+C to stop.\n');

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, async () => {
    await vite.close();
    backend.close();
    process.exit(0);
  });
}
