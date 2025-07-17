package com.mycompany.router.config;

public class RateLimitConfig {
    private int limit;
    private String period = "MINUTE";

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
