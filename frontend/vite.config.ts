import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // `npm run dev` serves on :5173 and forwards API calls to Spring Boot, so the browser sees a
    // single origin: the session cookie just works and no CORS setup is needed.
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    // Spring Boot serves anything in target/classes/static, so the build lands inside the jar.
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
});
