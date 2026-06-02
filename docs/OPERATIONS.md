# Operations

## Production deployment checklist

- [ ] **Replace `router.admin.apiKey`**: set `ADMIN_API_KEY` via Secret. Never commit the real value.
- [ ] **Front `/admin/**` with network policy** so it's only reachable from your bastion / control plane, not the public ingress.
- [ ] **Use managed Redis** (ElastiCache, MemoryDB, Memorystore, Aiven). Single-pod Redis in `deploy/k8s/redis.yaml` is for dev only.
- [ ] **Enable TLS to Redis** (`spring.data.redis.ssl.enabled=true`). The Lua script doesn't care; the connection does.
- [ ] **Kafka**: pre-create `gateway-access-log` with retention you actually want (`retention.ms=259200000` = 3 days), partitions ≥ 2× expected consumer parallelism, `min.insync.replicas=2`, `acks=1` (acks=all if you care about loss on broker failure — tradeoff is publish latency).
- [ ] **Set `JAVA_OPTS`** to constrain heap: `-XX:MaxRAMPercentage=70`. WebFlux + Netty allocates off-heap too; leave ~25% headroom.
- [ ] **PDB**: `minAvailable: 2` is already set. Scale up before draining nodes.
- [ ] **HPA**: tune `averageUtilization` from the provided 70%. Gateways tend to be CPU-bound under steady load; memory limit usually doesn't trigger first.
- [ ] **Sealed Secrets / External Secrets**: bind `ADMIN_API_KEY` via your secret manager, not the placeholder.
- [ ] **Run `scripts/smoke.sh`** against the deployed URL after every release.

## Scaling

The gateway scales horizontally with no coordination between instances *because state lives in Redis*. Adding a pod:
- Doesn't change anyone's rate-limit budget (buckets are keyed by `principalId`, shared in Redis).
- Doesn't require restart of others.
- Picks up the same `ConfigMap` — dynamic service edits via `/admin/services` propagate per-instance though; if you change a service via REST on instance A, instance B won't see it until you re-issue the call. For real fleets either restart on config change or push updates via a Redis pub/sub (extension exercise).

Vertical scaling: most gateways are CPU-bound on JSON parsing and TLS termination. Reactor-netty handles ~10k concurrent connections per pod at 1 CPU comfortably. If you see CPU saturation before request budget, scale out, not up.

## Troubleshooting

### Access log drops

Symptom: `gateway_access_log_dropped_total` is climbing.

Likely causes (in order):
1. **Sink backpressure** — Kafka can't keep up. Check broker health, partition count, network. If real, increase `router.accessLog.queueCapacity` *temporarily* while you scale Kafka. The right fix is more brokers / partitions, not a bigger buffer.
2. **Sink throwing** — check logs for `Access log sink {name} failed`. Pipeline keeps consuming, but events that hit the failing `publish()` are still counted as "dropped" only if the queue overflowed first.
3. **Burst beyond capacity** — flash spike (e.g. retry storm). The queue is supposed to drop in this case. Confirm via `gateway_requests_total` rate.

### 429s climbing for legitimate users

- Check `gateway_rate_limited_total` partitioned by principal id (add a tag in `RouterHandler` if not already).
- Confirm the resolved limit: hit `/admin/services/{svc}` and inspect `clientRateLimits` and `defaultRateLimit`. Tier policy is at `router.rateLimits.tiers`.
- If a single principal is responsible for legitimate burstiness, bump *their* `clientRateLimits` entry rather than the service default.

### Latency spikes

- Look at `gateway_request_latency_seconds` p99 vs p50. A widening gap usually means an upstream is slow on a subset of routes. Cross-reference with the access log: `latency_ms` per `target_url`.
- The gateway itself adds ~1–2 ms when Redis is local. If you see Redis latency > 5 ms, your Redis is too far away or saturated.

### Redis full

Token-bucket keys auto-expire at `period + 1s`. API key keys do **not** expire. If you mint millions of keys without deletion, plan for it. Expected steady-state Redis memory: `(active principals × number of services) × ~100 bytes + (number of API keys × ~300 bytes)`.

## Metrics worth alerting on

| Metric | Alert when | Why |
|---|---|---|
| `gateway_access_log_dropped_total` rate | > 0 for > 5m | You're losing audit data |
| `gateway_request_latency_seconds{quantile="0.99"}` | > 500 ms for > 5m | Upstreams or RL slow |
| `gateway_rate_limited_total` rate | > 5% of `gateway_requests_total` | Misconfigured limits or abuse |
| Pod restart count | > 0 in 1h | OOM or hard crash — investigate logs |
| Redis CPU | > 70% sustained | Lua script is hot; consider scale-up or cluster |
| Kafka producer error rate | > 0 sustained | Brokers unhealthy; access log will start dropping next |

## Rotating the admin key

1. Generate new value (`openssl rand -base64 32`).
2. Update Secret (sealed-secrets / external-secrets).
3. Rolling restart Deployment.
4. Verify with `curl -H "X-Admin-Key: <new>" /admin/services` → 200.

No coordination needed across pods — the key is read from env at startup.

## Rotating an API key

```bash
# Issue a new key
curl -X POST $GW/admin/apikeys \
  -H "X-Admin-Key: $ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"principalId":"alice","tier":"premium"}'
# → {"id":"...","key":"<give to alice>","principalId":"alice","tier":"premium"}

# Once alice has switched, delete the old one
curl -X DELETE $GW/admin/apikeys/{oldId} -H "X-Admin-Key: $ADMIN"
```

Deletes are immediate across the fleet because both indexes (`apikey:id:*` and `apikey:hash:*`) are in shared Redis.
