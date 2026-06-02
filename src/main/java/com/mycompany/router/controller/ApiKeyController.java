package com.mycompany.router.controller;

import com.mycompany.router.auth.ApiKey;
import com.mycompany.router.auth.ApiKeyHasher;
import com.mycompany.router.auth.ApiKeyStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/admin/apikeys")
public class ApiKeyController {

    private final ApiKeyStore store;

    public ApiKeyController(ApiKeyStore store) {
        this.store = store;
    }

    @GetMapping
    public Flux<ApiKey> list() {
        return store.list();
    }

    @PostMapping
    public Mono<ResponseEntity<IssuedKey>> create(@RequestBody CreateRequest req) {
        String raw = UUID.randomUUID().toString().replace("-", "");
        String id = req.id() == null ? UUID.randomUUID().toString() : req.id();
        ApiKey key = new ApiKey(
                id,
                ApiKeyHasher.sha256Hex(raw),
                req.principalId(),
                req.tier() == null ? "basic" : req.tier(),
                req.scopes() == null ? Set.of() : new HashSet<>(req.scopes()),
                true,
                Instant.now(),
                req.expiresAt());
        return store.save(key).map(saved ->
                ResponseEntity.ok(new IssuedKey(saved.id(), raw, saved.principalId(), saved.tier())));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        return store.delete(id).thenReturn(ResponseEntity.<Void>noContent().build());
    }

    public record CreateRequest(String id, String principalId, String tier,
                                List<String> scopes, Instant expiresAt) {}

    public record IssuedKey(String id, String key, String principalId, String tier) {}
}
