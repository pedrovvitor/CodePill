/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true, // Keycloak redirect URIs and collector CORS are pinned to 5173
    proxy: {
      // Same-origin API in dev: avoids backend CORS and keeps traceparent flowing.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      // lcov feeds the SonarQube scan in CI
      reporter: ['text', 'html', 'lcov'],
      // TESTING_QUALITY.md §2 — 85% floor; exclusions limited to bootstrap
      // wiring (src/app, main.tsx) and generated code (api-types.gen.ts).
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/app/**',
        'src/main.tsx',
        'src/domain/catalog/api-types.gen.ts',
        'src/test/**',
        'src/**/*.test.{ts,tsx}',
        'src/**/*.d.ts',
      ],
      thresholds: {
        lines: 85,
        branches: 85,
        functions: 85,
        statements: 85,
      },
    },
  },
})
