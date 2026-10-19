# Prototype architecture

Companion to the README's run instructions — these diagrams show what's actually in this
`sklum-backend-prototype/` directory, not the full SKLUM system design (see `SKLUM_System_Architecture` for
that; this is a zoom into Fig. 2/4/5 as they were actually built). Renders natively on GitHub,
GitLab, and most Markdown viewers (Mermaid, no extra tooling needed).

## 1. Runtime topology

What's actually running, on what port, talking to what. Postgres itself starts empty — the
backend applies schema, seed data, and indexes via Flyway on startup (see README → "Database
migrations"). This is a single Maven module (`backend/`), not split by layer — see CLAUDE.md's
"Module split" note for when that would change.

```mermaid
graph LR
    REVIEWER["Reviewer / curl / Postman / Swagger UI"]

    subgraph DOCKER[Docker Compose]
        PG[("Postgres 55432 / pg_trgm enabled / products, promise_lookup, orders, saga_steps")]
    end

    subgraph MOCKS[mock-backends, port 8081]
        MCC["CommerceCoreController /commerce-core/*"]
        MPAY["PaymentController /payment/*"]
        MCAR["CarrierController /carrier/a/*, /carrier/b/*"]
        MSTORE[("in-memory store, own data, not Postgres")]
        MCC --- MSTORE
        MPAY --- MSTORE
        MCAR --- MSTORE
    end

    subgraph BACKENDAPP[backend, port 8080]
        BACKEND["Spring Boot app: Home, Search, PDP, Checkout, Dashboard, Ops"]
    end

    REVIEWER -->|HTTP 8080| BACKEND
    BACKEND -->|JDBC 55432| PG
    BACKEND -->|HTTP 8081, circuit breaker wrapped| MCC
    BACKEND -->|HTTP 8081, circuit breaker wrapped| MPAY
    BACKEND -->|HTTP 8081, circuit breaker wrapped| MCAR
```

<details>
<summary>If your Mermaid renderer still shows nothing, click for a plain-text fallback</summary>

```
Reviewer (curl / Postman / Swagger UI)
        |  HTTP :8080
        v
  backend (Spring Boot, :8080) -- JDBC :55432 --> Postgres (Docker, pg_trgm)
        |
        | HTTP :8081 (circuit-breaker wrapped)
        v
  mock-backends (:8081)
    - CommerceCoreController  /commerce-core/*
    - PaymentController       /payment/*
    - CarrierController       /carrier/a/*, /carrier/b/*
    (own in-memory store, separate from Postgres)
```
</details>

## 2. Backend internal components

Same shape as the system design's Fig. 4 (which calls this the "BFF"), reflecting what was
actually coded — package names are the real `com.sklum.backend.*` packages.

```mermaid
graph LR
    subgraph WEB[web, controllers]
        HC[HomeController]
        SC[SearchController]
        PC[PdpController]
        CC[CheckoutController]
        DC[DashboardController]
    end

    subgraph SVC[service]
        HS[HomeService]
        SS[SearchService]
        CS[CatalogService]
        DPS[DeliveryPromiseService]
        OSO[OrderSagaOrchestrator]
        PPJ["PromisePrecomputeJob, Scheduled"]
    end

    subgraph CLI[client, Resilience4j circuit breaker + retry]
        CCC[CommerceCoreClient]
        PAC[PaymentClient]
        CAC[CarrierClient]
    end

    subgraph REPO[repository, NamedParameterJdbcTemplate]
        PR[ProductRepository]
        PLR[PromiseLookupRepository]
        OR[OrderRepository]
        IR[IdempotencyRepository]
    end

    subgraph OBS[observability]
        RLF[RequestLoggingFilter]
        MET[Micrometer meters]
        OSS[OpsSummaryService]
    end

    HC --> HS --> OR
    SC --> SS --> PR
    PC --> CS --> PR
    CS --> DPS
    CC --> OSO
    OSO -->|claim/replay key| IR
    OSO -->|refine promise| DPS
    DPS --> PLR
    DPS -->|live inventory check| CCC
    OSO -->|reserve, confirm| CCC
    OSO -->|charge, refund| PAC
    OSO --> OR
    PPJ -->|inventory snapshot| CCC
    PPJ -->|capacity, ETA| CAC
    PPJ -->|write| PLR
    DC --> OSS
    SS -.records.-> MET
    DPS -.records.-> MET
    OSS -.reads.-> MET
    OSS -.reads.-> OR
    OSS -.reads.-> PLR
```

## 3. Checkout saga, as a sequence

The actual control flow of `OrderSagaOrchestrator.checkout()` — every branch below has a real
test case behind it (see README → "Try the saga's compensation paths", or the Postman
collection's `simulateFailure` requests).

```mermaid
sequenceDiagram
    actor R as Reviewer
    participant Saga as OrderSagaOrchestrator
    participant Idem as IdempotencyRepository
    participant Promise as DeliveryPromiseService
    participant CC as CommerceCoreClient
    participant Pay as PaymentClient
    participant Mock as mock-backends

    R->>Saga: POST /checkout {sku, qty, postalCode, simulateFailure?} + Idempotency-Key?
    opt Idempotency-Key present
        Saga->>Idem: tryClaim(key, orderId)
        alt key already COMPLETED
            Idem-->>Saga: stored response
            Saga-->>R: 200 (replayed, unchanged — saga NOT re-run)
        else key still IN_PROGRESS (concurrent duplicate)
            Idem-->>Saga: no claim
            Saga-->>R: 409 Conflict
        end
    end
    Note over Saga,Idem: Only a brand-new key (or no key at all) reaches the saga below.
    Saga->>Promise: checkoutPromise(sku, postalCode)
    Promise->>CC: inventory(sku)  — the one live call
    CC->>Mock: GET /commerce-core/inventory/{sku}
    Mock-->>CC: stock per warehouse
    Promise-->>Saga: PromiseWindow (warehouse, carrier, days)

    Saga->>CC: reserve(orderId, sku, warehouseId, qty)
    CC->>Mock: POST /commerce-core/reservations
    alt reserve fails
        Mock-->>CC: 409/500
        Saga-->>R: 200 { status: "FAILED", sagaSteps: ["RESERVE:FAILED"] }
    else reserve ok
        Mock-->>CC: 200 { reservationId }
        Saga->>Pay: charge(orderId, amountCents)
        Pay->>Mock: POST /payment/charges
        alt charge fails
            Mock-->>Pay: 402/500
            Saga->>CC: release(reservationId)
            CC->>Mock: POST /commerce-core/reservations/{id}/release
            Saga-->>R: 200 { status: "COMPENSATED", sagaSteps: [..., "COMPENSATE_RESERVE:SUCCESS"] }
        else charge ok
            Mock-->>Pay: 200 { chargeId }
            Saga->>CC: confirm(orderId)
            CC->>Mock: POST /commerce-core/orders/{id}/confirm
            alt confirm fails
                Mock-->>CC: 500
                Saga->>Pay: refund(chargeId)
                Saga->>CC: release(reservationId)
                Saga-->>R: 200 { status: "COMPENSATED", sagaSteps: [..., "COMPENSATE_CHARGE:SUCCESS", "COMPENSATE_RESERVE:SUCCESS"] }
            else confirm ok
                Mock-->>CC: 200
                Saga-->>R: 200 { status: "CONFIRMED", sagaSteps: [..., "CONFIRM:SUCCESS"] }
            end
        end
    end

    Note over Saga,Mock: Every step is also written to the orders / saga_steps tables<br/>(Postgres) and reflected in GET /ops/summary + the dashboard.<br/>If an Idempotency-Key was given, the final response is also saved to idempotency_keys.
```
