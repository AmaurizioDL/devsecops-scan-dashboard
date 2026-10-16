import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, /api is proxied to the dockerized backend (host port 8091), so the browser
// only ever talks to one origin and the backend needs no CORS config. In the container,
// nginx does the same proxying (see nginx.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.BACKEND_URL ?? 'http://localhost:8091',
    },
  },
});
