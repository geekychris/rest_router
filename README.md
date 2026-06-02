# REST Router — A Reactive API Gateway

A lightweight, reactive (Spring WebFlux) API gateway with:

- **Identity-aware rate limiting** — token buckets keyed by authenticated principal, backed by Redis so limits hold across N instances.
- **Pluggable auth** — API keys (default), bring-your-own `ApiKeyStore` for JWT/OAuth/etc.
- **Async access logging** — bounded queue with pluggable sinks (Kafka, stdout JSON, file, no-op). Lossy by design under back-pressure, with drop counters exposed as metrics.
- **Dynamic service registry** — REST admin API to add/remove/edit services without restart.
- **Routing strategies** — weighted, header-based, plus an interface for your own.
- **Plugin chain** — pre/post request hooks.
- **Observability** — Micrometer + Prometheus, request id propagation, structured access log.
- **First-class deployment artifacts** — Dockerfile, docker-compose stack, Kubernetes manifests (Deployment + Service + HPA + PDB + Ingress + ServiceMonitor).

## Architecture (one screen)

```
 ┌──────────────────┐     ┌────────────────┐     ┌────────────────┐
 │ ApiKeyAuthFilter │────▶│ AdminAuthFilter│────▶│ RouterHandler  │
 │  resolves        │     │ guards /admin/*│     │  route + RL +  │
 │  Principal       │     │                │     │  forward       │
 └────────┬─────────┘     └────────────────┘     └───┬────────┬───┘
          │  reads                                   │        │
          ▼                                          ▼        ▼
   ┌──────────────┐                          ┌─────────────┐ ┌────────────┐
   │ ApiKeyStore  │                          │ RateLimiter │ │ AccessLog  │
   │ in-mem|Redis │                          │ Redis Lua   │ │ Pipeline   │
   └──────────────┘                          └─────────────┘ └─────┬──────┘
                                                                  │
                                                       ┌──────────┼──────────┐
                                                       ▼          ▼          ▼
                                                    Kafka       stdout      file
```

Full sequence and component notes: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Quick start

### 1. Local — full stack

```bash
docker compose up --build
```

Brings up: gateway, Redis, Kafka (KRaft), Kafka UI (`:8081`), Prometheus (`:9090`), Grafana (`:3000`, admin/admin) and three mock backends.

```bash
# Anonymous request, hits the anonymous tier (30/min by default)
curl http://localhost:8080/servicea/anything

# Authenticated request (bootstrap key from application.yml)
curl http://localhost:8080/servicea/anything -H "X-API-Key: demo-basic-secret"

# Mint a new key
curl -X POST http://localhost:8080/admin/apikeys \
  -H "X-Admin-Key: local-admin-key" \
  -H "Content-Type: application/json" \
  -d '{"principalId":"alice","tier":"premium"}'
```

Run the smoke suite:

```bash
./scripts/smoke.sh
```

Load test:

```bash
k6 run scripts/loadtest.js
```

### 2. Local — JVM only

```bash
mvn spring-boot:run
```

You'll need Redis on `localhost:6379` (or set `REDIS_HOST`/`REDIS_PORT`) — or set `router.rateLimits.backend=local` and `router.auth.storage=in-memory` for a zero-dep dev mode.

### 3. Kubernetes

```bash
kubectl apply -k deploy/k8s
```

See [docs/OPERATIONS.md](docs/OPERATIONS.md) for production checklist (sealed secrets, managed Redis, multi-AZ, etc.).

## Configuration

Everything is under the `router.*` prefix in `application.yml`.

| Key | Default | What it does |
|---|---|---|
| `router.auth.enabled` | `true` | Master switch for API-key auth |
| `router.auth.apiKeyHeader` | `X-API-Key` | Header to read (also accepts `Authorization: Bearer`) |
| `router.auth.storage` | `in-memory` | `in-memory` or `redis` |
| `router.auth.bootstrapKeys[]` | `[]` | Keys to upsert on startup (for dev/demo) |
| `router.rateLimits.backend` | `redis` | `redis` or `local` (single-node) |
| `router.rateLimits.tiers.{name}` | — | Per-tier `{limit, period}` |
| `router.accessLog.enabled` | `true` | Master switch |
| `router.accessLog.queueCapacity` | `10000` | Bounded buffer size; overflow = dropped events |
| `router.accessLog.sink` | `stdout` | `kafka` \| `stdout` \| `file` \| `noop` |
| `router.accessLog.kafka.topic` | `gateway-access-log` | Kafka topic |
| `router.admin.apiKey` | `changeme` | Shared key for `/admin/**`. **Override via env.** |
| `router.services.{name}.requireAuth` | `false` | Reject anonymous callers on this service |
| `router.services.{name}.defaultRateLimit` | tier policy | Per-service override |
| `router.services.{name}.clientRateLimits.{principalId}` | — | Per-principal override |
| `router.services.{name}.routes[].stripPrefix` | `""` | Path prefix to strip before forwarding |

## Rate-limit resolution order

For each request, the limit that applies is the first match:

1. `services.{svc}.clientRateLimits.{principalId}`
2. `services.{svc}.defaultRateLimit`
3. `rateLimits.tiers.{principal.tier}`
4. Hard fallback: `60 / MINUTE`

Anonymous callers are bucketed by remote IP (`anon:1.2.3.4`).

## Admin API

All admin endpoints require `X-Admin-Key`.

| Method | Path | Body |
|---|---|---|
| GET | `/admin/services` | — |
| GET | `/admin/services/{name}` | — |
| POST | `/admin/services/{name}` | `ServiceConfig` JSON |
| PUT | `/admin/services/{name}` | `ServiceConfig` JSON |
| DELETE | `/admin/services/{name}` | — |
| GET | `/admin/apikeys` | — |
| POST | `/admin/apikeys` | `{principalId, tier, scopes?, expiresAt?}` → `{id, key, ...}` |
| DELETE | `/admin/apikeys/{id}` | — |

The minted `key` from `POST /admin/apikeys` is only returned once.

## Observability

- `GET /actuator/health/{liveness,readiness}`
- `GET /actuator/prometheus` — Micrometer metrics, including:
  - `gateway_requests_total`
  - `gateway_rate_limited_total`
  - `gateway_request_latency_seconds` (timer)
  - `gateway_access_log_published_total{sink=...}`
  - `gateway_access_log_dropped_total{sink=...}`

Each response carries `X-Request-Id` (echoed if the client supplied one) and `X-RateLimit-{Limit,Remaining}`. Rate-limited responses also carry `X-RateLimit-Retry-After-Ms` and `Retry-After`.

## Extending

See [docs/EXTENDING.md](docs/EXTENDING.md). Short version:

| What | Interface | How |
|---|---|---|
| New auth source | `ApiKeyStore` | Register a `@Bean` |
| New rate-limit algorithm | `RateLimiter` | Register a `@Bean` (overrides default) |
| New access-log sink | `AccessLogSink` | Register a `@Bean` and set `router.accessLog.sink=<your name>` |
| New routing strategy | `RouteSelectionStrategy` | `@Component` — picked up automatically |
| Request/response hook | `RouterPlugin` | `@Component` |

## Operating

See [docs/OPERATIONS.md](docs/OPERATIONS.md): deployment, scaling, troubleshooting the access-log queue, Redis sizing, Kafka topic config.

## Layout

```
src/main/java/com/mycompany/router/
  RouterApplication.java
  auth/         API key model, store, hasher, filter
  ratelimit/    Distributed + local token bucket, resolver
  accesslog/    Pipeline, event, sinks
  admin/        Admin auth filter
  config/       Properties + Spring wiring
  controller/   /admin/services, /admin/apikeys
  handler/      RouterHandler (the request flow)
  plugin/       RouterPlugin interface
  routing/      Strategies
  service/      ServiceRegistry

deploy/
  prometheus.yml
  k8s/          namespace, configmap, secret, deployment, service, hpa, pdb, ingress, redis, servicemonitor, kustomization

scripts/
  smoke.sh      curl-based end-to-end sanity
  loadtest.js   k6 load test
```
