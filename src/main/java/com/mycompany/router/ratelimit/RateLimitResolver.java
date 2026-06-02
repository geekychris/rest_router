package com.mycompany.router.ratelimit;

import com.mycompany.router.auth.Principal;
import com.mycompany.router.config.RateLimitConfig;
import com.mycompany.router.config.RouterProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Resolves the effective rate-limit policy for a (principal, service) pair.
 *
 * Lookup order — first match wins:
 * <ol>
 *   <li>service-level override for this principal id ({@code clientRateLimits[principalId]})</li>
 *   <li>service-level {@code defaultRateLimit}</li>
 *   <li>tier policy ({@code router.rateLimits.tiers[tier]})</li>
 *   <li>hard fallback: 60/minute</li>
 * </ol>
 */
@Component
public class RateLimitResolver {

    private static final RateLimitConfig FALLBACK = new RateLimitConfig(60, "MINUTE");

    private final RouterProperties props;

    public RateLimitResolver(RouterProperties props) {
        this.props = props;
    }

    public RateLimitConfig resolve(Principal principal, RouterProperties.ServiceConfig service) {
        RateLimitConfig override = service.getClientRateLimits().get(principal.id());
        if (override != null) return override;

        if (service.getDefaultRateLimit() != null) {
            return service.getDefaultRateLimit();
        }

        RateLimitConfig tier = props.getRateLimits().getTiers().get(principal.tier());
        if (tier != null) return tier;

        return FALLBACK;
    }

    public String key(String serviceName, Principal principal) {
        return "rl:" + serviceName + ":" + principal.id();
    }

    public static Duration period(String period) {
        return switch (period) {
            case "SECOND" -> Duration.ofSeconds(1);
            case "MINUTE" -> Duration.ofMinutes(1);
            case "HOUR" -> Duration.ofHours(1);
            case "DAY" -> Duration.ofDays(1);
            default -> throw new IllegalArgumentException("Invalid period: " + period);
        };
    }
}
