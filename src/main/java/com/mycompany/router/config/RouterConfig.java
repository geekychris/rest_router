package com.mycompany.router.config;

import com.mycompany.router.handler.RouterHandler;
import com.mycompany.router.routing.HeaderBasedStrategy;
import com.mycompany.router.routing.RouteSelectionStrategy;
import com.mycompany.router.routing.WeightedTrafficStrategy;
import com.mycompany.router.service.RateLimiterService;
import com.mycompany.router.service.ServiceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.List;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> routerFunction(RouterHandler handler) {
        return route(all(), handler::handleRequest);
    }

    @Bean
    public List<RouteSelectionStrategy> routingStrategies(HeaderBasedStrategy headerBasedStrategy,
                                                        WeightedTrafficStrategy weightedTrafficStrategy) {
        return List.of(headerBasedStrategy, weightedTrafficStrategy);
    }

    @Bean
    public RateLimiterService rateLimiterService() {
        return new RateLimiterService();
    }

    @Bean
    public ServiceRegistry serviceRegistry(RouterProperties routerProperties,
                                         RateLimiterService rateLimiterService) {
        ServiceRegistry registry = new ServiceRegistry(rateLimiterService);
        routerProperties.getServices().forEach(registry::registerService);
        return registry;
    }
}
