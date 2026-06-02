package com.mycompany.router.auth;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Single-node default and the store used by tests. */
public class InMemoryApiKeyStore implements ApiKeyStore {

    private final Map<String, ApiKey> byId = new ConcurrentHashMap<>();
    private final Map<String, ApiKey> byHash = new ConcurrentHashMap<>();

    @Override
    public Mono<ApiKey> findByHash(String keyHash) {
        ApiKey key = byHash.get(keyHash);
        return key == null ? Mono.empty() : Mono.just(key);
    }

    @Override
    public Mono<ApiKey> save(ApiKey key) {
        byId.put(key.id(), key);
        byHash.put(key.keyHash(), key);
        return Mono.just(key);
    }

    @Override
    public Mono<Void> delete(String id) {
        ApiKey removed = byId.remove(id);
        if (removed != null) {
            byHash.remove(removed.keyHash());
        }
        return Mono.empty();
    }

    @Override
    public Flux<ApiKey> list() {
        return Flux.fromIterable(byId.values());
    }
}
