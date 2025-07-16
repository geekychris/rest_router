package com.mycompany.router.config;

import com.mycompany.router.handler.RouterHandler;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> routerFunction(RouterHandler handler) {
        return route(all(), handler::handleRequest);
    }

    @Bean
    public Map<String, RateLimiter> rateLimiters(RouterProperties routerProperties) {
        Map<String, RateLimiter> limiters = new HashMap<>();
        
        routerProperties.getServices().forEach((serviceName, config) -> {
            RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                    .limitForPeriod(config.getRateLimit())
                    .limitRefreshPeriod(Duration.parse("PT1" + config.getRateLimitPeriod()))
                    .timeoutDuration(Duration.ofSeconds(5))
                    .build();
            
            limiters.put(serviceName, RateLimiter.of(serviceName, rateLimiterConfig));
        });
        
        return limiters;
    }
}
