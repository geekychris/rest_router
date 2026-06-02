package com.mycompany.router.ratelimit;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Distributed (or local) rate limiter.
 *
 * Extension point: provide a different bean (e.g. Bucket4j-backed, sliding
 * window, leaky bucket) and the router will use it instead.
 */
public interface RateLimiter {

    /**
     * @param key     stable identifier for the bucket (e.g. {@code rl:servicea:user-42})
     * @param limit   bucket capacity (tokens)
     * @param period  time over which the bucket fully refills
     */
    Mono<RateLimitDecision> check(String key, long limit, Duration period);
}
