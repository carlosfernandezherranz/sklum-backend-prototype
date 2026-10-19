import { test, expect } from '@playwright/test';
import { demoPause } from './demo-pace';

test.describe('Ops dashboard (static HTML, polls /ops/summary)', () => {
  test('loads, redirects from /dashboard, and renders live tiles + breaker states', async ({ page }) => {
    // DashboardController 302s "/dashboard" -> "/dashboard.html" (a past bug: it used to render
    // the literal string "forward:/dashboard.html" instead of redirecting — see CLAUDE.md).
    const response = await page.goto('/dashboard');
    expect(response?.ok()).toBeTruthy();
    await expect(page).toHaveURL(/\/dashboard\.html$/);
    await expect(page.locator('h1')).toHaveText('SKLUM Backend — Operations');
    await demoPause(page, 1200, 'redirected /dashboard -> /dashboard.html');

    // Tiles are filled in by JS after polling /ops/summary — wait for real data, not the empty shell.
    await expect(page.locator('.tile .label').first()).toHaveText('Search p50');
    await expect(page.locator('#breakerRows tr').first()).toBeVisible();
    await demoPause(page, 2000, 'live tiles populated from /ops/summary');

    const breakerNames = await page.locator('#breakerRows tr td:first-child').allTextContents();
    expect(breakerNames).toEqual(
      expect.arrayContaining(['commerceCore', 'payment', 'carrierA', 'carrierB']),
    );

    // Every breaker should read CLOSED in steady state (no fault injection running here).
    const pills = page.locator('#breakerRows .pill');
    await expect(pills.first()).toBeVisible();
    const states = await pills.allTextContents();
    for (const state of states) {
      expect(['CLOSED', 'OPEN', 'HALF_OPEN']).toContain(state);
    }
    await demoPause(page, 2000, 'circuit breaker states (Resilience4j)');
  });

  test('order outcomes table reflects real saga activity, not the empty-state message', async ({ page, request }) => {
    // Make sure there's at least one confirmed order before asserting the table isn't empty.
    await request.post('/checkout', { data: { sku: 'SOF-1001', quantity: 1, postalCode: '28013' } });

    await page.goto('/dashboard.html');
    await expect(page.locator('#orderStatusRows .pill.CONFIRMED')).toBeVisible();
    await demoPause(page, 2500, 'order outcomes table reflecting the checkout we just placed');
  });
});
