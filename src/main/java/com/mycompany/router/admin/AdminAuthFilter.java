package com.mycompany.router.admin;

import com.mycompany.router.config.RouterProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Gates every request under {@code /admin/**} behind a shared admin API key
 * passed as {@code X-Admin-Key}. Intentionally simple — rotate the key via
 * env var {@code ADMIN_API_KEY} (see {@code application.yml}).
 *
 * For production you'd front this with mTLS or an SSO proxy instead. The
 * key here keeps the surface safe by default.
 */
public class AdminAuthFilter implements WebFilter, Ordered {

    public static final int ORDER = -90; // after auth filter, before router

    private static final String HEADER = "X-Admin-Key";
    private static final String PREFIX = "/admin";

    private final RouterProperties.AdminConfig config;

    public AdminAuthFilter(RouterProperties.AdminConfig config) {
        this.config = config;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(PREFIX)) {
            return chain.filter(exchange);
        }
        String provided = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (provided == null || !config.getApiKey().equals(provided)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
