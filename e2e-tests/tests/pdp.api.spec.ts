import { test, expect } from '@playwright/test';

test.describe('GET /pdp/{sku} — product detail + coarse delivery promise', () => {
  test('known SKU returns product + a date-range promise, never an exact date', async ({ request }) => {
    const res = await request.get('/pdp/SOF-1001');
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.product.sku).toBe('SOF-1001');
    expect(body.promise.granularity).toBe('ZONE');
    expect(typeof body.promise.minDays).toBe('number');
    expect(typeof body.promise.maxDays).toBe('number');
    expect(body.promise.maxDays).toBeGreaterThanOrEqual(body.promise.minDays);
  });

  test('unknown SKU returns 404, not a 500', async ({ request }) => {
    const res = await request.get('/pdp/NOPE-9999');
    expect(res.status()).toBe(404);
  });

  test('an explicit postal code is accepted', async ({ request }) => {
    const res = await request.get('/pdp/SOF-1001?postalCode=28013');
    expect(res.ok()).toBeTruthy();
  });
});

test.describe('GET /home', () => {
  test('returns the my-deliveries widget shape', async ({ request }) => {
    const res = await request.get('/home');
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(Array.isArray(body.myDeliveries)).toBeTruthy();
  });
});
