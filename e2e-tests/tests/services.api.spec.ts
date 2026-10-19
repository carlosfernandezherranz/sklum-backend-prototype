import { test, expect } from '@playwright/test';

test.describe('services are up', () => {
  test('backend health is UP, including DB and circuit breakers', async ({ request }) => {
    const res = await request.get('/actuator/health');
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.status).toBe('UP');
    expect(body.components.db.status).toBe('UP');
    expect(body.components.circuitBreakers.status).toBe('UP');
    for (const breaker of ['commerceCore', 'payment', 'carrierA', 'carrierB']) {
      expect(body.components.circuitBreakers.details[breaker].details.state).toBe('CLOSED');
    }
  });

  test('mock-backends responds for a known SKU', async ({ request }) => {
    const res = await request.get('http://localhost:8081/commerce-core/inventory/SOF-1001');
    expect(res.ok()).toBeTruthy();
  });

  test('swagger / openapi docs are served', async ({ request }) => {
    const apiDocs = await request.get('/v3/api-docs');
    expect(apiDocs.ok()).toBeTruthy();
    const spec = await apiDocs.json();
    expect(spec.info.title).toContain('SKLUM Backend');
  });
});
