import { test, expect } from '@playwright/test';
import { demoPause } from './demo-pace';

test.describe('Swagger UI', () => {
  test('loads and lists the documented tags', async ({ page }) => {
    await page.goto('/swagger-ui/index.html');
    await expect(page).toHaveTitle(/Swagger UI/);

    // Swagger UI renders tag sections once /v3/api-docs has loaded — wait for the real content.
    await expect(page.locator('.swagger-ui .opblock-tag').first()).toBeVisible();
    const tagTexts = (await page.locator('.swagger-ui .opblock-tag').allTextContents()).join(' ');
    for (const tag of ['Home', 'Search', 'PDP', 'Checkout', 'Ops']) {
      expect(tagTexts).toContain(tag);
    }
    await demoPause(page, 2000, 'all 5 documented tags loaded from the live /v3/api-docs spec');

    // Tags start expanded by default — click the POST /checkout operation itself to open its
    // full details (parameters incl. Idempotency-Key, request/response schemas).
    await page.locator('.opblock-summary', { hasText: '/checkout' }).click();
    await expect(page.getByText('Idempotency-Key').first()).toBeVisible();
    await demoPause(page, 2500, 'POST /checkout expanded — simulateFailure + Idempotency-Key documented here');
  });
});
