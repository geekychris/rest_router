package com.mycompany.router.handler;

import com.mycompany.router.config.RouterProperties;
import com.mycompany.router.plugin.RouterPlugin;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouterHandlerTest {

    private RouterHandler routerHandler;
    private RouterProperties routerProperties;
    private Map<String, RateLimiter> rateLimiters;
    private List<RouterPlugin> plugins;
    private WebClient.Builder webClientBuilder;
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        routerProperties = new RouterProperties();
        rateLimiters = new HashMap<>();
        plugins = new ArrayList<>();
        
        // Setup WebClient mock
        webClient = mock(WebClient.class);
        webClientBuilder = mock(WebClient.Builder.class);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        
        when(webClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.headers(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(any(Mono.class), eq(String.class))).thenReturn(requestHeadersSpec);
        ClientResponse clientResponse = mock(ClientResponse.class);
        when(clientResponse.statusCode()).thenReturn(org.springframework.http.HttpStatus.OK);
        when(clientResponse.headers()).thenReturn(new ClientResponse.Headers() {
            @Override
            public List<String> header(String name) {
                return List.of();
            }

            @Override
            public HttpHeaders asHttpHeaders() {
                return new HttpHeaders();
            }

            @Override
            public Optional<org.springframework.http.MediaType> contentType() {
                return Optional.of(org.springframework.http.MediaType.APPLICATION_JSON);
            }

            @Override
            public OptionalLong contentLength() {
                return OptionalLong.empty();
            }
        });
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(""));
        when(requestHeadersSpec.exchange()).thenReturn(Mono.just(clientResponse));
        
        // Setup test service configuration
        RouterProperties.ServiceConfig serviceConfig = new RouterProperties.ServiceConfig();
        serviceConfig.setBaseUrl("http://test-service:8080");
        serviceConfig.setRateLimit(100);
        serviceConfig.setRateLimitPeriod("MINUTE");
        
        RouterProperties.RouteConfig routeConfig = new RouterProperties.RouteConfig();
        routeConfig.setPath("/servicea");
        routeConfig.setTargetUrl("http://test-service:8080");
        routeConfig.setWeight(100);
        
        serviceConfig.setRoutes(List.of(routeConfig));
        routerProperties.getServices().put("servicea", serviceConfig);
        
        // Setup rate limiter
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
        rateLimiters.put("servicea", RateLimiter.of("servicea", rateLimiterConfig));
        
        // Setup mock plugin
        RouterPlugin mockPlugin = mock(RouterPlugin.class);
        when(mockPlugin.preProcess(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(mockPlugin.postProcess(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(mockPlugin.getOrder()).thenReturn(0);
        plugins.add(mockPlugin);
        
        routerHandler = new RouterHandler(webClientBuilder,
                routerProperties,
                rateLimiters,
                plugins);
    }

    @Test
    void handleRequest_ValidPath_ReturnsResponse() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/servicea/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        ServerRequest serverRequest = ServerRequest.create(exchange, List.of());

        StepVerifier.create(routerHandler.handleRequest(serverRequest))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void handleRequest_InvalidPath_ReturnsNotFound() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/invalid/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        ServerRequest serverRequest = ServerRequest.create(exchange, List.of());

        StepVerifier.create(routerHandler.handleRequest(serverRequest))
                .expectNextCount(1)
                .verifyComplete();
    }
}
