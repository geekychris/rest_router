package com.mycompany.router.ratelimit;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-process token bucket — useful for single-node deploys and tests.
 *
 * Algorithm matches {@link RedisTokenBucketRateLimiter} exactly so swapping
 * backends is observably the same.
 */
public class LocalTokenBucketRateLimiter implements RateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<RateLimitDecision> check(String key, long limit, Duration period) {
        long now = System.currentTimeMillis();
        long periodMs = period.toMillis();

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(limit, now));
        synchronized (bucket) {
            long elapsed = Math.max(0, now - bucket.ts);
            double refill = (elapsed * (double) limit) / periodMs;
            bucket.tokens = Math.min(limit, bucket.tokens + refill);
            bucket.ts = now;

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return Mono.just(RateLimitDecision.allowed((long) bucket.tokens, limit));
            } else {
                double needed = 1.0 - bucket.tokens;
                long retryAfter = (long) Math.ceil((needed * periodMs) / limit);
                return Mono.just(RateLimitDecision.denied(0, retryAfter, limit));
            }
        }
    }

    private static final class Bucket {
        double tokens;
        long ts;

        Bucket(double tokens, long ts) {
            this.tokens = tokens;
            this.ts = ts;
        }
    }
}
