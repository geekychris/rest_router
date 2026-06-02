package com.mycompany.router.auth;

import com.mycompany.router.config.RouterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthFilterTest {

    private final InMemoryApiKeyStore store = new InMemoryApiKeyStore();
    private final RouterProperties.AuthConfig auth = new RouterProperties.AuthConfig();

    @Test
    void resolvesValidApiKey() {
        auth.setEnabled(true);
        store.save(new ApiKey("k1", ApiKeyHasher.sha256Hex("topsecret"),
                "user-1", "premium", Set.of(), true, Instant.now(), null)).block();

        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(store, auth);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/anything")
                        .header("X-API-Key", "topsecret"));

        WebFilterChain chain = ex -> Mono.empty();
        filter.filter(exchange, chain).block();

        Principal p = (Principal) exchange.getAttributes().get(Principal.CONTEXT_KEY);
        assertThat(p).isNotNull();
        assertThat(p.anonymous()).isFalse();
        assertThat(p.id()).isEqualTo("user-1");
        assertThat(p.tier()).isEqualTo("premium");
    }

    @Test
    void fallsBackToAnonymousWhenKeyUnknown() {
        auth.setEnabled(true);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(store, auth);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/x")
                        .header("X-API-Key", "nope"));

        filter.filter(exchange, ex -> Mono.empty()).block();
        Principal p = (Principal) exchange.getAttributes().get(Principal.CONTEXT_KEY);
        assertThat(p.anonymous()).isTrue();
        assertThat(p.tier()).isEqualTo("anonymous");
    }

    @Test
    void acceptsBearerToken() {
        auth.setEnabled(true);
        store.save(new ApiKey("k", ApiKeyHasher.sha256Hex("abc"),
                "bob", "basic", Set.of(), true, Instant.now(), null)).block();
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(store, auth);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/x")
                        .header("Authorization", "Bearer abc"));

        filter.filter(exchange, ex -> Mono.empty()).block();
        Principal p = (Principal) exchange.getAttributes().get(Principal.CONTEXT_KEY);
        assertThat(p.id()).isEqualTo("bob");
    }
}
