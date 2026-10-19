import { test, expect } from '@playwright/test';

test.describe('GET /ops/summary — observability snapshot', () => {
  test('exposes search latency, promise cache, saga, and circuit breaker state', async ({ request }) => {
    // Drive at least one search first so searchRequestCount/latency fields are non-zero.
    await request.get('/search?q=sofa');

    const res = await request.get('/ops/summary');
    expect(res.ok()).toBeTruthy();
    const body = await res.json();

    expect(body.searchRequestCount).toBeGreaterThan(0);
    expect(body.searchP50Ms).toBeGreaterThanOrEqual(0);
    expect(body.searchP95Ms).toBeGreaterThanOrEqual(body.searchP50Ms);
    expect(body.promiseLookupRows).toBeGreaterThan(0); // precompute job has run
    expect(typeof body.circuitBreakerStates.commerceCore).toBe('string');
    expect(typeof body.circuitBreakerStates.payment).toBe('string');
    expect(typeof body.circuitBreakerStates.carrierA).toBe('string');
    expect(typeof body.circuitBreakerStates.carrierB).toBe('string');
  });
});
