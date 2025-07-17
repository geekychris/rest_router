package com.mycompany.router.handler;

import com.mycompany.router.config.RouterProperties;
import com.mycompany.router.plugin.RouterPlugin;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import com.mycompany.router.service.RateLimiterService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.mycompany.router.service.ServiceRegistry;
import com.mycompany.router.routing.RouteSelectionStrategy;

@Component
public class RouterHandler {
    private static final Logger logger = LoggerFactory.getLogger(RouterHandler.class);
    private final WebClient webClient;
    private final ServiceRegistry serviceRegistry;
    private final RateLimiterService rateLimiterService;
    private final List<RouteSelectionStrategy> routingStrategies;
    private final List<RouterPlugin> plugins;
    private final Random random = new Random();

    public RouterHandler(WebClient.Builder webClientBuilder,
                        ServiceRegistry serviceRegistry,
                        RateLimiterService rateLimiterService,
                        List<RouteSelectionStrategy> routingStrategies,
                        List<RouterPlugin> plugins) {
        this.webClient = webClientBuilder.build();
        this.serviceRegistry = serviceRegistry;
        this.rateLimiterService = rateLimiterService;
        this.routingStrategies = routingStrategies;
        this.plugins = plugins.stream()
                .sorted((p1, p2) -> Integer.compare(p1.getOrder(), p2.getOrder()))
                .toList();
    }

    public Mono<ServerResponse> handleRequest(ServerRequest request) {
        String path = request.path();
        String serviceName = extractServiceName(path);
        
        RouterProperties.ServiceConfig serviceConfig = serviceRegistry.getService(serviceName);
        if (serviceConfig == null) {
            return ServerResponse.notFound().build();
        }

        return processWithPlugins(request)
                .transformDeferred(RateLimiterOperator.of(rateLimiterService.getRateLimiter(serviceConfig, request)))
                .flatMap(modifiedRequest -> {
                    Optional<RouterProperties.RouteConfig> selectedRoute = selectRoute(serviceConfig, modifiedRequest);
                    if (selectedRoute.isEmpty()) {
                        return ServerResponse.notFound().build();
                    }

                    logRequest(modifiedRequest);
                    
                    return forwardRequest(modifiedRequest, selectedRoute.get());
                });
    }

    private String extractServiceName(String path) {
        String[] parts = path.split("/");
        return parts.length > 1 ? parts[1] : "";
    }

    private Optional<RouterProperties.RouteConfig> selectRoute(RouterProperties.ServiceConfig serviceConfig,
                                                   ServerRequest request) {
        for (RouteSelectionStrategy strategy : routingStrategies) {
            Optional<RouterProperties.RouteConfig> selectedRoute = strategy.selectRoute(serviceConfig.getRoutes(), request);
            if (selectedRoute.isPresent()) {
                return selectedRoute;
            }
        }
        return Optional.empty();
    }

    private boolean matchesRoute(RouterProperties.RouteConfig route, ServerRequest request) {
        // Check path matching
        if (!request.path().startsWith(route.getPath())) {
            return false;
        }

        // Check headers
        for (Map.Entry<String, String> header : route.getHeaders().entrySet()) {
            if (!request.headers().header(header.getKey()).contains(header.getValue())) {
                return false;
            }
        }

        // Check query parameters
        for (Map.Entry<String, String> param : route.getParams().entrySet()) {
            if (!request.queryParams().get(param.getKey()).contains(param.getValue())) {
                return false;
            }
        }

        return true;
    }

    private Mono<ServerRequest> processWithPlugins(ServerRequest request) {
        Mono<ServerHttpRequest> processedRequest = Mono.just(request.exchange().getRequest());
        
        for (RouterPlugin plugin : plugins) {
            processedRequest = processedRequest.flatMap(plugin::preProcess);
        }

        return processedRequest.map(req -> ServerRequest.from(request).build());
    }

    private void logRequest(ServerRequest request) {
        // Log request details (implement your logging logic here)
        logger.info("Processing request: {} {}", request.method(), request.path());
    }

    private Mono<ServerResponse> forwardRequest(ServerRequest request,
                                              RouterProperties.RouteConfig routeConfig) {
        return webClient
                .method(request.method())
                .uri(routeConfig.getTargetUrl() + request.path())
                .headers(headers -> headers.putAll(request.headers().asHttpHeaders()))
                .body(request.bodyToMono(String.class), String.class)
                .exchange()
                .flatMap(clientResponse -> ServerResponse
                        .status(clientResponse.statusCode())
                        .headers(headers -> headers.putAll(clientResponse.headers().asHttpHeaders()))
                        .body(clientResponse.bodyToMono(String.class), String.class));
    }
}
