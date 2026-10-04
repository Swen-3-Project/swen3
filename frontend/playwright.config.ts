import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 30000,
  reporter: 'list',
  use: {
    baseURL: process.env['PAPERLESS_URL'] ?? 'http://localhost',
    headless: true,
    viewport: { width: 1280, height: 900 },
    locale: 'de-AT',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
