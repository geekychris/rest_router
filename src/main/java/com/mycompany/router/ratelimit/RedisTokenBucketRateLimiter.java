package com.mycompany.router.ratelimit;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Token bucket implemented as an atomic Redis Lua script.
 *
 * The script reads {tokens, ts}, refills proportionally to elapsed time at
 * rate {@code limit / periodMs} tokens/ms, deducts one token if available,
 * and writes back. It returns {allowed, remaining, retryAfterMs}.
 *
 * Why Lua rather than Bucket4j: zero extra dependencies, transparent algorithm,
 * works on any Redis 3.2+, easy to swap with a sliding-window or leaky-bucket
 * variant by replacing the script.
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final String SCRIPT = """
            local key       = KEYS[1]
            local capacity  = tonumber(ARGV[1])
            local periodMs  = tonumber(ARGV[2])
            local now       = tonumber(ARGV[3])
            local cost      = tonumber(ARGV[4])

            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts     = tonumber(data[2])

            if tokens == nil then
                tokens = capacity
                ts = now
            end

            local elapsed = math.max(0, now - ts)
            local refill  = (elapsed * capacity) / periodMs
            tokens = math.min(capacity, tokens + refill)

            local allowed = 0
            local retryAfter = 0

            if tokens >= cost then
                tokens = tokens - cost
                allowed = 1
            else
                local needed = cost - tokens
                retryAfter = math.ceil((needed * periodMs) / capacity)
            end

            redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, math.ceil(periodMs) + 1000)

            return {allowed, math.floor(tokens), retryAfter}
            """;

    private final ReactiveStringRedisTemplate redis;
    private final RedisScript<List> script;

    public RedisTokenBucketRateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
        this.script = RedisScript.of(SCRIPT, List.class);
    }

    @Override
    public Mono<RateLimitDecision> check(String key, long limit, Duration period) {
        long now = System.currentTimeMillis();
        long periodMs = period.toMillis();
        List<String> keys = List.of(key);
        List<String> args = List.of(
                Long.toString(limit),
                Long.toString(periodMs),
                Long.toString(now),
                "1");

        return redis.execute(script, keys, args)
                .next()
                .map(result -> {
                    @SuppressWarnings("unchecked")
                    List<Object> r = (List<Object>) result;
                    long allowed = toLong(r.get(0));
                    long remaining = toLong(r.get(1));
                    long retryAfterMs = toLong(r.get(2));
                    return allowed == 1
                            ? RateLimitDecision.allowed(remaining, limit)
                            : RateLimitDecision.denied(remaining, retryAfterMs, limit);
                });
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }
}
