package com.mycompany.router.auth;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryApiKeyStoreTest {

    @Test
    void saveAndLookupByHash() {
        InMemoryApiKeyStore store = new InMemoryApiKeyStore();
        String hash = ApiKeyHasher.sha256Hex("secret");
        ApiKey key = new ApiKey("id-1", hash, "alice", "basic", Set.of("read"),
                true, Instant.now(), null);

        store.save(key).block();

        StepVerifier.create(store.findByHash(hash))
                .assertNext(k -> {
                    assertThat(k.principalId()).isEqualTo("alice");
                    assertThat(k.tier()).isEqualTo("basic");
                })
                .verifyComplete();
    }

    @Test
    void deleteRemovesBothIndexes() {
        InMemoryApiKeyStore store = new InMemoryApiKeyStore();
        String hash = ApiKeyHasher.sha256Hex("s");
        ApiKey key = new ApiKey("id", hash, "p", "basic", Set.of(),
                true, Instant.now(), null);
        store.save(key).block();
        store.delete("id").block();

        StepVerifier.create(store.findByHash(hash)).verifyComplete();
    }
}
