package com.mycompany.router.auth;

import com.mycompany.router.config.RouterProperties;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolves a {@link Principal} for each request and puts it on the exchange
 * attributes and the Reactor context.
 *
 * Resolution order:
 * <ol>
 *   <li>{@code Authorization: Bearer &lt;key&gt;}</li>
 *   <li>configured api-key header (default {@code X-API-Key})</li>
 *   <li>anonymous, fingerprinted by remote IP</li>
 * </ol>
 *
 * The filter never rejects requests — that is the router's job, because it
 * knows which services require auth. The filter just resolves identity.
 */
public class ApiKeyAuthFilter implements WebFilter, Ordered {

    public static final int ORDER = -100;

    private final ApiKeyStore store;
    private final RouterProperties.AuthConfig authConfig;

    public ApiKeyAuthFilter(ApiKeyStore store, RouterProperties.AuthConfig authConfig) {
        this.store = store;
        this.authConfig = authConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!authConfig.isEnabled()) {
            Principal anon = Principal.anonymous(remoteAddr(exchange));
            exchange.getAttributes().put(Principal.CONTEXT_KEY, anon);
            return chain.filter(exchange)
                    .contextWrite(Context.of(Principal.CONTEXT_KEY, anon));
        }

        String rawKey = extractKey(exchange);
        Principal anon = Principal.anonymous(remoteAddr(exchange));

        if (rawKey == null) {
            exchange.getAttributes().put(Principal.CONTEXT_KEY, anon);
            return chain.filter(exchange)
                    .contextWrite(Context.of(Principal.CONTEXT_KEY, anon));
        }

        String hash = ApiKeyHasher.sha256Hex(rawKey);
        return store.findByHash(hash)
                .filter(k -> k.isUsable(java.time.Instant.now()))
                .map(this::toPrincipal)
                .defaultIfEmpty(anon)
                .flatMap(principal -> {
                    exchange.getAttributes().put(Principal.CONTEXT_KEY, principal);
                    return chain.filter(exchange)
                            .contextWrite(Context.of(Principal.CONTEXT_KEY, principal));
                });
    }

    private Principal toPrincipal(ApiKey k) {
        return new Principal(k.principalId(), k.tier(),
                k.scopes() == null ? Set.of() : new HashSet<>(k.scopes()),
                false);
    }

    private String extractKey(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return exchange.getRequest().getHeaders().getFirst(authConfig.getApiKeyHeader());
    }

    private String remoteAddr(ServerWebExchange exchange) {
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        if (addr == null || addr.getAddress() == null) return "unknown";
        return addr.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
