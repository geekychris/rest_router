package com.mycompany.router.config;

/**
 * Token-bucket rate limit configuration.
 *
 * The bucket has {@code limit} capacity and refills uniformly over {@code period}.
 * A single request costs one token.
 */
public class RateLimitConfig {
    private int limit;
    private String period = "MINUTE";

    public RateLimitConfig() {}

    public RateLimitConfig(int limit, String period) {
        this.limit = limit;
        this.period = period;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }
}
