package com.mycompany.router.auth;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Storage adapter for API keys.
 *
 * Extension point: provide your own bean to back keys from Postgres, Vault, an
 * SSO directory, etc. The router only ever calls {@link #findByHash}.
 */
public interface ApiKeyStore {

    Mono<ApiKey> findByHash(String keyHash);

    Mono<ApiKey> save(ApiKey key);

    Mono<Void> delete(String id);

    Flux<ApiKey> list();
}
