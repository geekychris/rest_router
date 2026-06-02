# Extending the gateway

Five extension points cover almost everything you'll want to change.

## 1. Custom `AccessLogSink` — ship logs anywhere

```java
@Component
public class S3Sink implements AccessLogSink {
    @Override public String name() { return "s3"; }
    @Override public void publish(AccessLogEvent event) {
        // batch + upload
    }
    @Override public void close() { /* flush */ }
}
```

Then in `application.yml`:

```yaml
router:
  accessLog:
    sink: s3   # matches your name()
```

Override `RouterConfig.accessLogSink(...)` if you want to pick by name from a list of beans instead of by config — that's how you support sinks discovered at runtime.

The pipeline guarantees:
- One thread calls `publish` (serialised).
- Buffer is bounded — exceptions in `publish` don't kill the dispatcher.
- `close()` is called on graceful shutdown.

## 2. Custom `ApiKeyStore` — auth from your own system

```java
@Component
public class JdbcApiKeyStore implements ApiKeyStore {
    @Override public Mono<ApiKey> findByHash(String keyHash) { ... }
    @Override public Mono<ApiKey> save(ApiKey key) { ... }
    @Override public Mono<Void> delete(String id) { ... }
    @Override public Flux<ApiKey> list() { ... }
}
```

Define your bean and either:
- Set `router.auth.storage` to something the default factory ignores so your `@Component` wins (Spring picks the most specific bean), or
- Mark the default factory `@ConditionalOnMissingBean(ApiKeyStore.class)` (the existing `RouterConfig` already wires it via `ApiKeyStore apiKeyStore(...)` — you can replace that method by providing your own bean and renaming the default).

The auth filter only ever calls `findByHash(sha256(rawKey))`. If your system stores keys differently (JWTs, opaque tokens, OAuth introspection), implement that lookup inside `findByHash` and ignore `save`/`delete`/`list` (return `Mono.empty()` / `Flux.empty()`).

## 3. Custom `RateLimiter` — a different algorithm

```java
@Component
public class SlidingWindowRateLimiter implements RateLimiter {
    @Override
    public Mono<RateLimitDecision> check(String key, long limit, Duration period) {
        // Redis sorted set with timestamps inside the window
    }
}
```

The provided default is registered via `RouterConfig.rateLimiter(...)`. Your `@Component` will take precedence — or change `RouterConfig` to pick between candidates.

Algorithm ideas:
- **Sliding window log** — exact, but memory cost = limit per key.
- **Sliding window counter** — approximate, two buckets, very cheap.
- **Leaky bucket** — smooths bursts harder than token bucket.
- **Bucket4j-backed** — if you'd rather trust a library.

## 4. Custom `RouteSelectionStrategy`

```java
@Component
public class GeoStrategy implements RouteSelectionStrategy {
    @Override
    public Optional<RouterProperties.RouteConfig> selectRoute(
            List<RouterProperties.RouteConfig> routes, ServerRequest req) {
        String country = req.headers().firstHeader("CF-IPCountry");
        return routes.stream()
                .filter(r -> country.equals(r.getHeaders().get("x-geo")))
                .findFirst();
    }
}
```

Update `RouterConfig.routingStrategies(...)` to include yours in the desired order — earlier strategies win.

## 5. Custom `RouterPlugin` — request/response hooks

```java
@Component
public class CorrelationPlugin implements RouterPlugin {
    @Override
    public Mono<ServerHttpRequest> preProcess(ServerHttpRequest request) {
        return Mono.just(request.mutate()
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .build());
    }
    @Override public Mono<ServerHttpResponse> postProcess(ServerHttpResponse response) {
        return Mono.just(response);
    }
    @Override public int getOrder() { return 10; }
}
```

Plugins are auto-discovered (any `RouterPlugin` `@Component`). Order field decides execution sequence; lower numbers run first.

## Reading the authenticated principal in your code

Anywhere downstream of the auth filter:

```java
Principal p = (Principal) exchange.getAttributes().get(Principal.CONTEXT_KEY);
```

Or from a Reactor context-aware operator:

```java
return Mono.deferContextual(ctx -> {
    Principal p = ctx.get(Principal.CONTEXT_KEY);
    ...
});
```

The principal is never null — at minimum you get an anonymous one keyed by IP.
