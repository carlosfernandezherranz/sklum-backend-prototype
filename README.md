# SKLUM Backend — domain deep-dive prototype

Implements Fig. 2/4/5 of `SKLUM_System_Architecture` (where this service is called the "BFF" —
named `backend` here in the prototype to avoid the jargon): search (Postgres trigram), the
two-tier delivery promise (precompute + live inventory check), the order saga (with compensation
and fault injection), and operational observability (structured logs, Micrometer, Resilience4j
circuit breakers, a tiny ops dashboard).

See **[ARCHITECTURE.md](ARCHITECTURE.md)** for diagrams of the pieces below: runtime topology,
the backend's internal components, and the checkout saga as a sequence diagram.

## Prerequisites

Java 25 (current LTS), Maven, Docker (with `docker compose` or the standalone `docker-compose`).
If `java -version` on your PATH isn't 25, point `JAVA_HOME` at a JDK 25 install for the `mvn`
commands below (e.g. `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` on macOS, or wherever
your JDK 25 is installed) — an older JDK compiling/running this will fail.

## Run it

Three pieces, started **in order**, each one needs to be ready before the next step — the backend
fails fast at startup if Postgres isn't reachable yet, and the precompute job needs mock-backends
up to populate `promise_lookup` on first run.

```bash
# Point Maven at a real JDK 25 for the two mvn steps below (adjust the path to your install):
export JAVA_HOME=/opt/homebrew/opt/openjdk@25   # macOS/Homebrew example used while building this

# 1. Postgres — starts EMPTY (schema/seed are Flyway migrations, applied by the backend on
#    startup, not Docker init scripts), mapped to host port 55432 (see note below)
docker compose up -d
# wait for it to be healthy:
until docker compose ps postgres | grep -q healthy; do sleep 2; done

# 2. Mock backends — port 8081 (separate terminal, or background it)
cd mock-backends && mvn spring-boot:run
# wait for it in another terminal:
until curl -sf http://localhost:8081/commerce-core/inventory/SOF-1001 >/dev/null; do sleep 2; done

# 3. The backend — port 8080 (another terminal). Flyway runs its 3 migrations against the empty
#    Postgres from step 1 automatically on startup (schema, seed data, indexes) — nothing to
#    run by hand.
cd backend && mvn spring-boot:run
# ready when this returns {"status":"UP",...}:
curl -s http://localhost:8080/actuator/health
```

Then:
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html — call every endpoint interactively
- **Ops dashboard**: http://localhost:8080/dashboard — latency, saga outcomes, circuit breaker states
- **Postman**: import `postman/SKLUM-Backend.postman_collection.json` (includes the three saga fault-injection requests pre-built)
- **OpenAPI**: `openapi.yaml` (hand-written, offline-readable) or the live `GET /v3/api-docs`

### To stop everything

```bash
# Ctrl-C each mvn spring-boot:run individually — do NOT `pkill -f spring-boot:run`,
# it matches both mock-backends and backend and kills the wrong one too.
docker compose down          # add -v to also wipe the data volume
```

To reset to a clean slate (e.g. after changing a migration): `docker compose down -v && docker compose up -d`,
then start the backend — Flyway re-runs all migrations against the fresh, empty database. A plain
restart (no `-v`) leaves the data alone and Flyway correctly does nothing, since
`flyway_schema_history` already records all three migrations as applied.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Backend logs `FATAL: role "sklum" does not exist` | It connected to a *different* Postgres (e.g. a native one already on 5432), not this project's container | Confirm `lsof -nP -iTCP:5432 -sTCP:LISTEN` — if something else owns 5432, this is exactly why the container is mapped to 55432 instead. Don't change the container back to 5432 unless you've checked nothing else is using it. |
| `/checkout` returns `"commerce-core unavailable (circuit open)"` even on a normal request | mock-backends isn't running, or was restarted and the circuit breaker is still open from before | Confirm `curl localhost:8081/commerce-core/inventory/SOF-1001` works, then retry — the breaker auto-recovers (half-open → closed) on the next successful call once the backend is back. |
| `/search` returns 0 results for an obviously-matching term | Checked already — see CLAUDE.md's "Search had a real bug" note; this was fixed (`word_similarity`, not `similarity`). If it regresses, check `pg_trgm.word_similarity_threshold` is still set at the database level. |
| `promiseLookupRows: 0` on `/ops/summary` right after startup | The precompute job's startup run hasn't finished yet (it iterates every SKU × warehouse × zone) | Wait a few seconds and re-poll; it also re-runs every 10 minutes (`promise.precompute.interval-ms`). |
| `mvn spring-boot:run` fails with `Unsupported class file major version 69` | `JAVA_HOME` points at a JDK older than 25 — class file major version 69 *is* Java 25's bytecode, and the Maven process running the plugin can't read it | `export JAVA_HOME` to a real JDK 25 before running `mvn` (see Prerequisites) — `javac --release 25` alone isn't enough if the JVM running Maven itself is older. |

> **Port note**: Postgres is mapped to host port **55432**, not the default 5432 — this
> machine already runs a native Postgres for other projects on 5432, and this avoids touching
> it. If you're running this elsewhere with nothing else on 5432, you can change it back in
> `docker-compose.yml` and `backend/src/main/resources/application.yml`.

## Try the saga's compensation paths

```bash
# Happy path — CONFIRMED
curl -X POST localhost:8080/checkout -H 'Content-Type: application/json' \
  -d '{"sku":"SOF-1001","quantity":1,"postalCode":"28013"}'

# Force payment to fail — COMPENSATED (reservation released)
curl -X POST localhost:8080/checkout -H 'Content-Type: application/json' \
  -d '{"sku":"SOF-1001","quantity":1,"postalCode":"28013","simulateFailure":"payment"}'

# Force confirm to fail — COMPENSATED (payment refunded, then reservation released)
curl -X POST localhost:8080/checkout -H 'Content-Type: application/json' \
  -d '{"sku":"SOF-1001","quantity":1,"postalCode":"28013","simulateFailure":"confirm"}'
```

Watch `sagaSteps` in the response, or `GET /ops/summary` / the dashboard for the running counts
(`saga_steps` table in Postgres has the full audit trail).

## Retrying a checkout safely (Idempotency-Key)

Pass an `Idempotency-Key` header (any client-generated unique string, e.g. a UUID) to retry the
*same* checkout — after a client-side timeout, a dropped response, a flaky connection — without
reserving stock or charging payment a second time. The same key replays the original saga's
result unchanged:

```bash
curl -X POST localhost:8080/checkout -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 3f29c4de-6b9a-4e2a-9b7a-2e6a6a8f0c11' \
  -d '{"sku":"SOF-1001","quantity":1,"postalCode":"28013"}'
# Run the exact same command again — identical response, same orderId, stock only consumed once.
```

Omit the header and the old behavior applies (every call is a brand-new order). A second request
reusing a key whose first request is still mid-saga gets `409 Conflict`, not a race — see
CLAUDE.md's "Idempotency key" decision for why this prototype rejects that case rather than
blocking-and-waiting for the first one to finish.

## Run the tests (Playwright)

With all three pieces above already running:

```bash
cd e2e-tests
npm install
npx playwright install chromium   # once
npx playwright test
```

21 tests: API coverage for search/PDP/checkout-saga/ops, plus two browser tests (dashboard,
Swagger UI). See **[e2e-tests/README.md](e2e-tests/README.md)** for what each file checks and a
real limitation worth knowing about (the saga tests consume finite demo inventory, same as a real
checkout would).

## What's deliberately simplified

- ~24 SKUs, not ~4,500 — enough to exercise search/promise/saga, not a catalog-scale dataset.
- Delivery promise precomputes 3 postal zones (Madrid/Barcelona/Paris) with 2 sample postcodes
  each, not full EU postcode coverage — same reasoning, see `PromisePrecomputeJob`.
- No auth — `/home`'s "my deliveries" widget shows the 5 most recent orders globally, not a
  logged-in customer's own orders.
- Mock Commerce Core/Payment/Carrier are one consolidated Spring Boot app with route groups
  (`/commerce-core`, `/payment`, `/carrier/{a,b}`), each with its own in-memory state — see
  CLAUDE.md's "Mock topology" decision for why one service instead of three.
- One Maven module for the whole backend (`web`/`service`/`client`/`repository` as packages, not
  separate Maven modules) — see CLAUDE.md's "Module split" note for why, and when that would change.

## Project layout

```
docker-compose.yml                             Postgres only — starts empty, no init scripts
mock-backends/                                 stubbed Commerce Core / Payment / Carrier (port 8081)
backend/                                       the actual prototype (port 8080)
backend/src/main/resources/db/migration/       Flyway migrations (schema, seed, indexes) — see below
openapi.yaml                                   hand-written OpenAPI 3.0 spec
postman/                                       Postman collection, incl. fault-injection requests
e2e-tests/                                     Playwright API + browser tests (see e2e-tests/README.md)
```

### Database migrations (Flyway)

Four migrations, applied automatically by the backend on startup (`backend/src/main/resources/db/migration/`):

| Migration | What it does |
|---|---|
| `V1__schema.sql` | `pg_trgm` extension, the word-similarity threshold, all 6 tables, the 4 indexes needed from day one (3 trigram GIN indexes for search, 1 for the promise lookup's cross-warehouse read pattern) |
| `V2__seed.sql` | the ~24 demo SKUs, 3 warehouses, stock levels |
| `V3__add_performance_indexes.sql` | `orders.created_at` (used by the home-screen "recent deliveries" query) and `saga_steps.order_id` (the FK — not auto-indexed by Postgres, needed for a per-order saga history) |
| `V4__add_idempotency_keys.sql` | `idempotency_keys` table backing the `Idempotency-Key` header on `POST /checkout` (see above) |

This replaced an earlier version that used Postgres's Docker init-script mechanism
(`/docker-entrypoint-initdb.d`) directly — that only runs once, on a brand-new empty volume, so
there was no way to evolve the schema without wiping all data. Flyway tracks what's applied in
`flyway_schema_history` and only runs what's new.
