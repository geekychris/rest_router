package com.mycompany.router.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Redis-backed API key store. Keys are JSON-encoded under two indexes:
 * <ul>
 *   <li>{@code apikey:id:{id}}     → ApiKey JSON</li>
 *   <li>{@code apikey:hash:{hash}} → ApiKey JSON</li>
 * </ul>
 * Both writes are issued; lookups go through the hash index for O(1) auth.
 */
public class RedisApiKeyStore implements ApiKeyStore {

    private static final String ID_PREFIX = "apikey:id:";
    private static final String HASH_PREFIX = "apikey:hash:";

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisApiKeyStore(ReactiveStringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public Mono<ApiKey> findByHash(String keyHash) {
        return redis.opsForValue().get(HASH_PREFIX + keyHash)
                .flatMap(this::decode);
    }

    @Override
    public Mono<ApiKey> save(ApiKey key) {
        String json;
        try {
            json = mapper.writeValueAsString(key);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        return redis.opsForValue().set(ID_PREFIX + key.id(), json)
                .then(redis.opsForValue().set(HASH_PREFIX + key.keyHash(), json))
                .thenReturn(key);
    }

    @Override
    public Mono<Void> delete(String id) {
        return redis.opsForValue().get(ID_PREFIX + id)
                .flatMap(this::decode)
                .flatMap(existing ->
                        redis.delete(ID_PREFIX + id, HASH_PREFIX + existing.keyHash()).then())
                .switchIfEmpty(redis.delete(ID_PREFIX + id).then());
    }

    @Override
    public Flux<ApiKey> list() {
        return redis.keys(ID_PREFIX + "*")
                .flatMap(k -> redis.opsForValue().get(k))
                .flatMap(this::decode);
    }

    private Mono<ApiKey> decode(String json) {
        try {
            return Mono.just(mapper.readValue(json, ApiKey.class));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
