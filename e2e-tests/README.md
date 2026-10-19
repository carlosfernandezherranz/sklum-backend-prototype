# E2E tests (Playwright)

API + browser tests against the **running** prototype stack. This suite does not start
Postgres/mock-backends/backend itself — see `../README.md` → "Run it" first. `global-setup.ts`
checks both services are reachable before any test runs and fails fast with that same pointer
if they aren't, instead of letting 21 tests time out one by one.

## Run

```bash
npm install
npx playwright install chromium   # once
npx playwright test               # headless
npx playwright test --headed      # watch the browser-based tests run
npx playwright show-report        # open the HTML report from the last run
```

Override the target host if needed: `BACKEND_URL=... MOCKS_URL=... npx playwright test`.

## What's covered

| File | Covers |
|---|---|
| `services.api.spec.ts` | Health endpoint (DB + all 4 circuit breakers UP), mock-backends reachable, OpenAPI docs served |
| `search.api.spec.ts` | Exact match, typo-tolerant match (`word_similarity`), no false positives, `limit` |
| `pdp.api.spec.ts` | Product + zone-level promise shape, **404 on unknown SKU**, `/home` |
| `checkout-saga.api.spec.ts` | All three real saga outcomes: CONFIRMED, FAILED (reserve), COMPENSATED ×2 (payment, confirm) — asserts the exact `sagaSteps` trail each one leaves, and that `/ops/summary` counters reflect them |
| `idempotency.api.spec.ts` | `Idempotency-Key` replay returns an identical response without re-running the saga; a genuinely concurrent duplicate key splits 200/409; omitting the header keeps the old un-deduplicated behavior |
| `ops-summary.api.spec.ts` | Latency percentiles, promise cache, breaker states all present and sane |
| `dashboard.browser.spec.ts` | `/dashboard` → `/dashboard.html` redirect, tiles render from live data, breaker + order-outcome rows populate |
| `swagger.browser.spec.ts` | Swagger UI loads and lists all 5 documented tags (Home/Search/PDP/Checkout/Ops) |

## A real limitation, not a flaky test

The checkout/saga tests place real orders against `mock-backends`' in-memory stock for
`SOF-1001`. Each `CONFIRMED` or `COMPENSATED` run consumes (or releases) stock exactly like a
real checkout would — there's no reset endpoint. Enough repeated full-suite runs will eventually
exhaust that SKU's stock at the warehouse the promise engine picks, which turns a payment/confirm
*injected* failure into a genuine `RESERVE:FAILED` (a different, real outcome, not a bug in the
test or the app). If you see that: restart `mock-backends` (`mvn spring-boot:run` again) to reset
its in-memory state back to the seed data, the same way `docker compose down -v && up -d` resets
Postgres.
