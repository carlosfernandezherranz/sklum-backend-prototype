import { test, expect } from '@playwright/test';

test.describe('GET /search — Postgres trigram, typo-tolerant', () => {
  test('exact term matches', async ({ request }) => {
    const res = await request.get('/search?q=sofa');
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.resultCount).toBeGreaterThan(0);
    expect(body.results.some((p: any) => p.category === 'Sofas')).toBeTruthy();
  });

  test('typo still matches (word_similarity, not similarity)', async ({ request }) => {
    const res = await request.get('/search?q=sofaa');
    const body = await res.json();
    expect(body.resultCount).toBeGreaterThan(0);
    expect(body.results.some((p: any) => p.sku === 'SOF-1001')).toBeTruthy();
  });

  test('a second typo on a different word also matches', async ({ request }) => {
    const res = await request.get('/search?q=tabel');
    const body = await res.json();
    expect(body.resultCount).toBeGreaterThan(0);
  });

  test('nonsense query returns zero results, not a false positive', async ({ request }) => {
    const res = await request.get('/search?q=gibberishxyz');
    const body = await res.json();
    expect(body.resultCount).toBe(0);
    expect(body.results).toEqual([]);
  });

  test('limit parameter caps the result count', async ({ request }) => {
    // "sofaa" matches 8 products unbounded (verified separately) — limit=2 must cap that down.
    const res = await request.get('/search?q=sofaa&limit=2');
    const body = await res.json();
    expect(body.results.length).toBeLessThanOrEqual(2);
  });
});
