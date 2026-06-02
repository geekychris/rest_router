package com.mycompany.router.handler;

import com.mycompany.router.accesslog.AccessLogEvent;
import com.mycompany.router.accesslog.AccessLogPipeline;
import com.mycompany.router.auth.Principal;
import com.mycompany.router.config.RateLimitConfig;
import com.mycompany.router.config.RouterProperties;
import com.mycompany.router.plugin.RouterPlugin;
import com.mycompany.router.ratelimit.RateLimitDecision;
import com.mycompany.router.ratelimit.RateLimitResolver;
import com.mycompany.router.ratelimit.RateLimiter;
import com.mycompany.router.routing.RouteSelectionStrategy;
import com.mycompany.router.service.ServiceRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class RouterHandler {

    private static final Logger log = LoggerFactory.getLogger(RouterHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final WebClient webClient;
    private final ServiceRegistry serviceRegistry;
    private final RateLimiter rateLimiter;
    private final RateLimitResolver rateLimitResolver;
    private final List<RouteSelectionStrategy> routingStrategies;
    private final List<RouterPlugin> plugins;
    private final AccessLogPipeline accessLog;
    private final Counter requestsCounter;
    private final Counter rateLimitedCounter;
    private final Timer latencyTimer;

    public RouterHandler(WebClient.Builder webClientBuilder,
                         ServiceRegistry serviceRegistry,
                         RateLimiter rateLimiter,
                         RateLimitResolver rateLimitResolver,
                         List<RouteSelectionStrategy> routingStrategies,
                         List<RouterPlugin> plugins,
                         AccessLogPipeline accessLog,
                         MeterRegistry meterRegistry) {
        this.webClient = webClientBuilder.build();
        this.serviceRegistry = serviceRegistry;
        this.rateLimiter = rateLimiter;
        this.rateLimitResolver = rateLimitResolver;
        this.routingStrategies = routingStrategies;
        this.plugins = plugins.stream()
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .toList();
        this.accessLog = accessLog;
        this.requestsCounter = Counter.builder("gateway.requests").register(meterRegistry);
        this.rateLimitedCounter = Counter.builder("gateway.rate_limited").register(meterRegistry);
        this.latencyTimer = Timer.builder("gateway.request.latency").register(meterRegistry);
    }

    public Mono<ServerResponse> handleRequest(ServerRequest request) {
        long start = System.nanoTime();
        Instant startInstant = Instant.now();
        String requestId = ensureRequestId(request);
        String path = request.path();
        String serviceName = extractServiceName(path);
        Principal principal = (Principal) request.exchange().getAttributes()
                .getOrDefault(Principal.CONTEXT_KEY, Principal.anonymous("none"));

        RouterProperties.ServiceConfig serviceConfig = serviceRegistry.getService(serviceName);
        if (serviceConfig == null) {
            return finish(request, principal, serviceName, null, HttpStatus.NOT_FOUND.value(),
                    start, startInstant, requestId, "unknown-service", null,
                    ServerResponse.notFound().build());
        }

        if (serviceConfig.isRequireAuth() && principal.anonymous()) {
            return finish(request, principal, serviceName, null, HttpStatus.UNAUTHORIZED.value(),
                    start, startInstant, requestId, "auth-required", null,
                    ServerResponse.status(HttpStatus.UNAUTHORIZED).build());
        }

        RateLimitConfig limitCfg = rateLimitResolver.resolve(principal, serviceConfig);
        String rlKey = rateLimitResolver.key(serviceName, principal);
        Duration period = RateLimitResolver.period(limitCfg.getPeriod());

        return rateLimiter.check(rlKey, limitCfg.getLimit(), period)
                .flatMap(decision -> {
                    requestsCounter.increment();
                    if (!decision.allowed()) {
                        rateLimitedCounter.increment();
                        return finish(request, principal, serviceName, null,
                                HttpStatus.TOO_MANY_REQUESTS.value(),
                                start, startInstant, requestId, "rate-limited", null,
                                rateLimitedResponse(decision));
                    }
                    return route(request, principal, serviceName, serviceConfig, decision,
                            start, startInstant, requestId);
                });
    }

    private Mono<ServerResponse> route(ServerRequest request,
                                       Principal principal,
                                       String serviceName,
                                       RouterProperties.ServiceConfig serviceConfig,
                                       RateLimitDecision decision,
                                       long start,
                                       Instant startInstant,
                                       String requestId) {
        return processWithPlugins(request)
                .flatMap(modifiedRequest -> {
                    Optional<RouterProperties.RouteConfig> selectedRoute =
                            selectRoute(serviceConfig, modifiedRequest);
                    if (selectedRoute.isEmpty()) {
                        return finish(modifiedRequest, principal, serviceName, null,
                                HttpStatus.NOT_FOUND.value(), start, startInstant, requestId,
                                "no-route", null, ServerResponse.notFound().build());
                    }
                    RouterProperties.RouteConfig route = selectedRoute.get();
                    return forwardRequest(modifiedRequest, route, requestId, decision)
                            .flatMap(resp -> finish(modifiedRequest, principal, serviceName,
                                    route.getTargetUrl(), resp.statusCode().value(),
                                    start, startInstant, requestId, null, null,
                                    Mono.just(resp)))
                            .onErrorResume(e -> finish(modifiedRequest, principal, serviceName,
                                    route.getTargetUrl(), HttpStatus.BAD_GATEWAY.value(),
                                    start, startInstant, requestId, e.getClass().getSimpleName(), e,
                                    ServerResponse.status(HttpStatus.BAD_GATEWAY).build()));
                });
    }

    private Mono<ServerResponse> finish(ServerRequest request,
                                        Principal principal,
                                        String serviceName,
                                        String targetUrl,
                                        int status,
                                        long startNs,
                                        Instant startInstant,
                                        String requestId,
                                        String error,
                                        Throwable cause,
                                        Mono<ServerResponse> responseMono) {
        long latencyNs = System.nanoTime() - startNs;
        latencyTimer.record(Duration.ofNanos(latencyNs));
        long latencyMs = latencyNs / 1_000_000L;
        if (cause != null) {
            log.warn("Gateway error reqId={} service={} target={}", requestId, serviceName, targetUrl, cause);
        }
        emit(request, principal, serviceName, targetUrl, status, latencyMs, startInstant, requestId, error);
        return responseMono;
    }

    private void emit(ServerRequest request,
                      Principal principal,
                      String serviceName,
                      String targetUrl,
                      int status,
                      long latencyMs,
                      Instant timestamp,
                      String requestId,
                      String error) {
        long requestBytes = request.headers().contentLength().orElse(0);
        String ua = request.headers().firstHeader("User-Agent");
        String clientIp = request.remoteAddress()
                .map(a -> a.getAddress() == null ? "unknown" : a.getAddress().getHostAddress())
                .orElse("unknown");
        AccessLogEvent ev = new AccessLogEvent(
                requestId,
                timestamp,
                request.method().name(),
                request.path(),
                request.uri().getQuery() == null ? "" : request.uri().getQuery(),
                serviceName,
                targetUrl,
                status,
                latencyMs,
                requestBytes,
                0L,
                principal.id(),
                principal.tier(),
                clientIp,
                ua == null ? "" : ua,
                error == null ? "" : error);
        accessLog.offer(ev);
    }

    private Mono<ServerResponse> rateLimitedResponse(RateLimitDecision d) {
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", Long.toString(d.limit()))
                .header("X-RateLimit-Remaining", Long.toString(d.remaining()))
                .header("X-RateLimit-Retry-After-Ms", Long.toString(d.retryAfterMs()))
                .header("Retry-After", Long.toString(Math.max(1, d.retryAfterMs() / 1000)))
                .build();
    }

    private String ensureRequestId(ServerRequest request) {
        String existing = request.headers().firstHeader(REQUEST_ID_HEADER);
        return existing != null ? existing : UUID.randomUUID().toString();
    }

    private String extractServiceName(String path) {
        String[] parts = path.split("/");
        return parts.length > 1 ? parts[1] : "";
    }

    private Optional<RouterProperties.RouteConfig> selectRoute(RouterProperties.ServiceConfig serviceConfig,
                                                               ServerRequest request) {
        for (RouteSelectionStrategy strategy : routingStrategies) {
            Optional<RouterProperties.RouteConfig> selected = strategy.selectRoute(serviceConfig.getRoutes(), request);
            if (selected.isPresent()) return selected;
        }
        return Optional.empty();
    }

    private Mono<ServerRequest> processWithPlugins(ServerRequest request) {
        Mono<ServerHttpRequest> processed = Mono.just(request.exchange().getRequest());
        for (RouterPlugin plugin : plugins) {
            processed = processed.flatMap(plugin::preProcess);
        }
        return processed.thenReturn(request);
    }

    private Mono<ServerResponse> forwardRequest(ServerRequest request,
                                                RouterProperties.RouteConfig routeConfig,
                                                String requestId,
                                                RateLimitDecision rateLimit) {
        String forwardPath = rewritePath(request.path(), routeConfig.getStripPrefix());
        String query = request.uri().getRawQuery();
        String fullUri = routeConfig.getTargetUrl() + forwardPath + (query == null ? "" : "?" + query);

        return webClient
                .method(request.method())
                .uri(fullUri)
                .headers(headers -> {
                    headers.putAll(request.headers().asHttpHeaders());
                    headers.set(REQUEST_ID_HEADER, requestId);
                    headers.remove("Host");
                })
                .body(request.bodyToMono(byte[].class), byte[].class)
                .exchangeToMono(clientResponse ->
                        clientResponse.bodyToMono(byte[].class)
                                .defaultIfEmpty(new byte[0])
                                .flatMap(body -> ServerResponse
                                        .status(clientResponse.statusCode())
                                        .headers(h -> {
                                            h.putAll(clientResponse.headers().asHttpHeaders());
                                            h.set("X-Request-Id", requestId);
                                            h.set("X-RateLimit-Limit", Long.toString(rateLimit.limit()));
                                            h.set("X-RateLimit-Remaining", Long.toString(rateLimit.remaining()));
                                        })
                                        .bodyValue(body)));
    }

    private String rewritePath(String inboundPath, String stripPrefix) {
        if (stripPrefix == null || stripPrefix.isEmpty()) return inboundPath;
        if (inboundPath.startsWith(stripPrefix)) {
            String rest = inboundPath.substring(stripPrefix.length());
            return rest.isEmpty() ? "/" : rest;
        }
        return inboundPath;
    }
}
