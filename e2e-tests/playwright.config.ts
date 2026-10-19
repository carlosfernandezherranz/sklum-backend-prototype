import { defineConfig } from '@playwright/test';

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';

// DEMO=1 slows browser actions down and adds narrated pauses (see tests/demo-pace.ts) so a
// human watching --headed can actually follow what's happening, instead of it flashing by in
// under a second. Off by default so a normal/CI run stays fast.
const DEMO = !!process.env.DEMO;
const SLOW_MO = process.env.SLOWMO ? Number(process.env.SLOWMO) : (DEMO ? 400 : 0);

export default defineConfig({
  testDir: './tests',
  fullyParallel: false, // the checkout tests share server-side saga/order state, keep it simple
  workers: DEMO ? 1 : undefined, // one at a time in demo mode, so windows don't race each other
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  globalSetup: './global-setup.ts',
  timeout: DEMO ? 120_000 : undefined,
  use: {
    baseURL: BACKEND_URL,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'api',
      testMatch: /.*\.api\.spec\.ts/,
    },
    {
      name: 'chromium',
      testMatch: /.*\.browser\.spec\.ts/,
      use: { browserName: 'chromium', launchOptions: { slowMo: SLOW_MO } },
    },
  ],
});
