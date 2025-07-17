package com.mycompany.router.service;

import com.mycompany.router.config.RateLimitConfig;
import com.mycompany.router.config.RouterProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public RateLimiter getRateLimiter(RouterProperties.ServiceConfig serviceConfig, ServerRequest request) {
        String clientId = extractClientId(serviceConfig, request);
        String rateLimiterKey = createRateLimiterKey(serviceConfig, clientId);
        
        return rateLimiters.computeIfAbsent(rateLimiterKey,
                key -> createRateLimiter(serviceConfig, clientId, key));
    }

    private String extractClientId(RouterProperties.ServiceConfig serviceConfig, ServerRequest request) {
        return request.headers()
                .firstHeader(serviceConfig.getClientIdHeader());
    }

    private String createRateLimiterKey(RouterProperties.ServiceConfig serviceConfig, String clientId) {
        return clientId != null ? serviceConfig.getBaseUrl() + ":" + clientId : serviceConfig.getBaseUrl();
    }

    private RateLimiter createRateLimiter(RouterProperties.ServiceConfig serviceConfig,
                                        String clientId,
                                        String rateLimiterKey) {
        RateLimitConfig rateLimitConfig = serviceConfig.getClientRateLimits().get(clientId);
        if (rateLimitConfig == null) {
            rateLimitConfig = serviceConfig.getDefaultRateLimit();
        }

RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(rateLimitConfig.getLimit())
                .limitRefreshPeriod(parseDuration(rateLimitConfig.getPeriod()))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        return RateLimiter.of(rateLimiterKey, config);
    }

    public void updateRateLimiter(String serviceName,
                                RouterProperties.ServiceConfig serviceConfig) {
        // Remove existing rate limiters for this service to force recreation with new config
        rateLimiters.entrySet().removeIf(entry -> entry.getKey().startsWith(serviceConfig.getBaseUrl()));
    }
    private Duration parseDuration(String period) {
        switch (period) {
            case "SECOND":
                return Duration.ofSeconds(1);
            case "MINUTE":
                return Duration.ofMinutes(1);
            case "HOUR":
                return Duration.ofHours(1);
            case "DAY":
                return Duration.ofDays(1);
            default:
                throw new IllegalArgumentException("Invalid period: " + period);
        }
    }
}
