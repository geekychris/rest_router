package com.mycompany.router.auth;

import java.util.Set;

/**
 * Resolved caller identity for a single request. Lives in the Reactor context
 * under {@link #CONTEXT_KEY} and on the {@code ServerWebExchange} attribute
 * map under the same key.
 */
public record Principal(
        String id,
        String tier,
        Set<String> scopes,
        boolean anonymous) {

    public static final String CONTEXT_KEY = "router.principal";

    public static Principal anonymous(String fingerprint) {
        return new Principal("anon:" + fingerprint, "anonymous", Set.of(), true);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
