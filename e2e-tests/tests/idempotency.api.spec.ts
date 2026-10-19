import { test, expect } from '@playwright/test';

/**
 * Idempotency-Key on POST /checkout — a retry of the same request (e.g. after a client-side
 * timeout) must replay the original saga's result, not reserve/charge/confirm a second time.
 * See CLAUDE.md's "Idempotency key" decision and OrderSagaOrchestrator.checkout(request, key).
 */
test.describe('POST /checkout — Idempotency-Key', () => {
  test('a repeated key returns the identical response instead of re-running the saga', async ({ request }) => {
    const key = `playwright-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const body = { sku: 'SOF-1001', quantity: 1, postalCode: '28013' };

    const first = await request.post('/checkout', { headers: { 'Idempotency-Key': key }, data: body });
    const firstBody = await first.json();
    expect(firstBody.status).toBe('CONFIRMED');

    const replay = await request.post('/checkout', { headers: { 'Idempotency-Key': key }, data: body });
    const replayBody = await replay.json();

    expect(replayBody).toEqual(firstBody); // same orderId, same sagaSteps, byte-for-byte
  });

  test('a key reused while the first request is still in flight gets 409, not a race', async ({ request }) => {
    const key = `playwright-race-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const body = { sku: 'SOF-1001', quantity: 1, postalCode: '28013' };

    const [first, second] = await Promise.all([
      request.post('/checkout', { headers: { 'Idempotency-Key': key }, data: body }),
      request.post('/checkout', { headers: { 'Idempotency-Key': key }, data: body }),
    ]);

    const statuses = [first.status(), second.status()].sort();
    // One wins (200, saga ran once), the other is rejected as a concurrent duplicate (409).
    expect(statuses).toEqual([200, 409]);
  });

  test('omitting the header keeps the old, un-deduplicated behavior (two separate orders)', async ({ request }) => {
    const body = { sku: 'SOF-1001', quantity: 1, postalCode: '28013' };
    const a = await request.post('/checkout', { data: body });
    const b = await request.post('/checkout', { data: body });
    const aBody = await a.json();
    const bBody = await b.json();
    expect(aBody.orderId).not.toBe(bBody.orderId);
  });
});
