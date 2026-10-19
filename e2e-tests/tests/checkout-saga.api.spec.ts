import { test, expect } from '@playwright/test';

/**
 * Covers the saga's three real outcomes (see ARCHITECTURE.md's sequence diagram and
 * CLAUDE.md's saga decision): every branch below is a genuine compensating-transaction path,
 * not a mocked assertion — each request round-trips through the real orchestrator, the real
 * mock-backends process, and Postgres (orders/saga_steps).
 */
test.describe('POST /checkout — saga with compensation', () => {
  test('happy path: CONFIRMED with a full RESERVE/CHARGE/CONFIRM trail', async ({ request }) => {
    const res = await request.post('/checkout', {
      data: { sku: 'SOF-1001', quantity: 1, postalCode: '28013' },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.status).toBe('CONFIRMED');
    expect(body.orderId).toBeTruthy();
    expect(body.sagaSteps).toEqual(
      expect.arrayContaining(['RESERVE:SUCCESS', 'CHARGE:SUCCESS', 'CONFIRM:SUCCESS']),
    );
    expect(body.promise.minDays).toBeDefined();
  });

  test('reserve fails: FAILED, no compensation needed (nothing was reserved yet)', async ({ request }) => {
    const res = await request.post('/checkout', {
      data: { sku: 'SOF-1001', quantity: 1, postalCode: '28013', simulateFailure: 'reserve' },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.status).toBe('FAILED');
    expect(body.sagaSteps).toContain('RESERVE:FAILED');
    expect(body.sagaSteps.some((s: string) => s.startsWith('COMPENSATE'))).toBeFalsy();
  });

  test('payment fails: COMPENSATED by releasing the reservation', async ({ request }) => {
    const res = await request.post('/checkout', {
      data: { sku: 'SOF-1001', quantity: 1, postalCode: '28013', simulateFailure: 'payment' },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.status).toBe('COMPENSATED');
    expect(body.sagaSteps).toEqual(
      expect.arrayContaining(['RESERVE:SUCCESS', 'CHARGE:FAILED', 'COMPENSATE_RESERVE:SUCCESS']),
    );
  });

  test('confirm fails: COMPENSATED by refunding the payment, then releasing the reservation', async ({ request }) => {
    const res = await request.post('/checkout', {
      data: { sku: 'SOF-1001', quantity: 1, postalCode: '28013', simulateFailure: 'confirm' },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.status).toBe('COMPENSATED');
    expect(body.sagaSteps).toEqual(
      expect.arrayContaining([
        'RESERVE:SUCCESS',
        'CHARGE:SUCCESS',
        'CONFIRM:FAILED',
        'COMPENSATE_CHARGE:SUCCESS',
        'COMPENSATE_RESERVE:SUCCESS',
      ]),
    );
  });

  test('every outcome above is reflected in /ops/summary counters', async ({ request }) => {
    const res = await request.get('/ops/summary');
    const body = await res.json();
    expect(body.orderStatusCounts.CONFIRMED).toBeGreaterThan(0);
    expect(body.orderStatusCounts.COMPENSATED).toBeGreaterThan(0);
    expect(body.sagaStepCounts.RESERVE).toBeGreaterThan(0);
    expect(body.sagaStepCounts.CHARGE).toBeGreaterThan(0);
  });
});
