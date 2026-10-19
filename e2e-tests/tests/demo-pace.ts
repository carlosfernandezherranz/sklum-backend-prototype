import { Page, test } from '@playwright/test';

/**
 * Pure demo pacing, not a correctness wait — every real wait in these specs already uses
 * expect(...).toBeVisible()/toHaveText() etc. This just gives a human watching --headed time to
 * read what's on screen before the test moves on. No-op unless DEMO=1 (see playwright.config.ts).
 */
export async function demoPause(page: Page, ms = 1500, label?: string) {
  if (!process.env.DEMO) return;
  if (label) test.info().annotations.push({ type: 'demo', description: label });
  await page.waitForTimeout(ms);
}
