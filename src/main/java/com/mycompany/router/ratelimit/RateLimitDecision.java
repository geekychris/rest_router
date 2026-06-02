package com.mycompany.router.ratelimit;

/**
 * Outcome of a single rate-limit check.
 *
 * @param allowed    whether the request may proceed
 * @param remaining  tokens left in the bucket after this check
 * @param retryAfterMs how long until the bucket has at least one token, if {@code !allowed}
 * @param limit      the bucket's nominal capacity (helps clients display headers)
 */
public record RateLimitDecision(boolean allowed, long remaining, long retryAfterMs, long limit) {

    public static RateLimitDecision allowed(long remaining, long limit) {
        return new RateLimitDecision(true, remaining, 0, limit);
    }

    public static RateLimitDecision denied(long remaining, long retryAfterMs, long limit) {
        return new RateLimitDecision(false, remaining, retryAfterMs, limit);
    }
}
