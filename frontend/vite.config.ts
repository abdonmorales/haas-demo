import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  // `--mode pages` is the static GitHub Pages build: relative asset paths so it works under
  // /<repo>/, and output in frontend/dist for the Pages workflow to upload.
  base: mode === 'pages' ? './' : '/',
  server: {
    // `npm run dev` serves on :5173 and forwards API calls to Spring Boot, so the browser sees a
    // single origin: the session cookie just works and no CORS setup is needed.
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    // Spring Boot serves anything in target/classes/static, so the build lands inside the jar.
    outDir: mode === 'pages' ? 'dist' : '../target/classes/static',
    emptyOutDir: true,
  },
}));
