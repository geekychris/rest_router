# Architecture

## Request flow

```
Client ──HTTP──▶ ApiKeyAuthFilter (WebFilter, order=-100)
                  │ resolves Principal from X-API-Key or Authorization
                  │ falls back to anonymous(remote-ip) on miss
                  ▼
                AdminAuthFilter (WebFilter, order=-90)
                  │ pass-through for non-/admin
                  │ 401 if /admin/* without X-Admin-Key
                  ▼
                RouterHandler  (the catch-all RouterFunction)
                  ├─ extract service name from /{service}/...
                  ├─ enforce service.requireAuth (reject anonymous)
                  ├─ RateLimitResolver → bucket policy
                  ├─ RateLimiter.check(key, limit, period)
                  │     denied? → 429 with X-RateLimit-Retry-After-Ms
                  ├─ run RouterPlugin pre-processors (ordered)
                  ├─ pick a RouteConfig via RouteSelectionStrategy chain
                  ├─ rewrite path (stripPrefix)
                  ├─ forward via WebClient (reactor-netty)
                  └─ emit AccessLogEvent → bounded queue → sink
                  ▼
                upstream service
```

## Components

### `auth/`

- **`Principal`** — record `{id, tier, scopes, anonymous}`. Stashed on the `ServerWebExchange` attributes under `"router.principal"` and in the Reactor context. Anonymous principals are bucketed by remote IP fingerprint.
- **`ApiKey`** — persisted record `{id, keyHash, principalId, tier, scopes, enabled, createdAt, expiresAt}`. The raw secret is **never** persisted; only `sha256(secret)`.
- **`ApiKeyStore`** — interface. Implementations: `InMemoryApiKeyStore` (default for tests / single-node), `RedisApiKeyStore` (default for prod).
- **`ApiKeyAuthFilter`** — `WebFilter` at order `-100`. Always sets *some* principal (anon or real) so downstream code never needs a null check.

### `ratelimit/`

- **`RateLimiter`** — single-method interface `Mono<RateLimitDecision> check(key, limit, period)`.
- **`RedisTokenBucketRateLimiter`** — a Lua script `EVAL`'d atomically in Redis. The script reads `{tokens, ts}`, refills proportionally to elapsed time at rate `limit/periodMs`, deducts one token if available, writes back. Returns `{allowed, remaining, retryAfterMs}`. Algorithm chosen for transparency (no third-party rate-limit lib) and portability (any Redis ≥3.2).
- **`LocalTokenBucketRateLimiter`** — in-process fallback for tests and single-node deploys. Matches the Redis algorithm so behaviour is observably identical.
- **`RateLimitResolver`** — resolves the effective `{limit, period}` for a `(principal, service)` pair using the documented precedence order.

### `accesslog/`

- **`AccessLogEvent`** — flat record (no nesting) so consumers can map fields to columns trivially.
- **`AccessLogPipeline`** — `ArrayBlockingQueue<AccessLogEvent>` + one daemon dispatcher thread + Micrometer counters for `published` and `dropped`. **Lossy under back-pressure by design** — we never block the request thread on a slow sink.
- **`AccessLogSink`** — `{KafkaSink, StdoutJsonSink, FileSink, NoopSink}`. Add your own as a `@Bean`.

### `handler/RouterHandler`

The orchestrator. Owns:
- Request id propagation (`X-Request-Id`).
- Rate-limit decision → 429 short-circuit.
- Plugin chain execution.
- Route selection + path rewriting.
- WebClient forwarding (with error → 502).
- Timer + counter metrics.
- Access-log emission (always, including for rejected requests).

### `config/`

- **`RouterProperties`** — typed Spring `ConfigurationProperties` for everything under `router.*`.
- **`RouterConfig`** — `@Configuration`. Wires beans conditionally based on properties:
  - Redis present + `auth.storage=redis` → `RedisApiKeyStore` (else `InMemoryApiKeyStore`).
  - Redis present + `rateLimits.backend=redis` → `RedisTokenBucketRateLimiter` (else local).
  - `accessLog.sink` → matching `AccessLogSink`.
  - `bootstrapKeys[]` → seeded via `BootstrapKeysLoader` on `@PostConstruct`.

## State that lives in Redis

| Key | Type | Purpose |
|---|---|---|
| `apikey:id:{id}` | string (JSON) | API key indexed by id |
| `apikey:hash:{hash}` | string (JSON) | API key indexed by SHA-256 of secret — auth path |
| `rl:{service}:{principalId}` | hash `{tokens, ts}` | Token-bucket state. TTL set to `periodMs + 1s` so idle buckets self-clean. |

Nothing else in Redis is load-bearing. A flushed Redis = full bucket refills + no API keys until the bootstrap loader runs again (or until you re-issue keys via `/admin/apikeys`).

## Failure modes

| Failure | Behaviour |
|---|---|
| Redis unavailable, `backend=redis` | Auth calls error out (`findByHash` Mono errors). Mitigation: set `backend=local` per-instance, or wrap the store with a small TTL cache + fail-open policy (extension exercise). |
| Kafka unavailable, `sink=kafka` | Pipeline buffers up to `queueCapacity`, then drops. Drops are counted (`gateway_access_log_dropped_total`). |
| Upstream slow / down | `WebClient` returns an error → 502, error name in access log, `error` field non-empty. |
| Plugin throws | Pre-processor errors propagate out → 500 + access log entry. (Production tip: wrap plugins with `.onErrorResume` if they should be optional.) |

## Why these choices

- **Reactive end-to-end (WebFlux + Lettuce + reactor-netty)** so one instance handles thousands of in-flight calls on a small thread pool. No blocking on Redis or Kafka in the request path.
- **Redis Lua over Bucket4j / external lib** — algorithm is one screen of code, no version coupling, swappable for a sliding-window variant by replacing the script.
- **Bounded lossy access-log queue over guaranteed-delivery** — gateways must never wedge on observability infrastructure. Drops are *visible* (counter) so SREs can resize the queue when they see them.
- **Identity at the edge** — every component below the filter reads `Principal` from the exchange attribute, so plugins, rate limit, metrics, and access log all agree on who the caller is.
