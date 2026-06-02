package com.mycompany.router.auth;

import java.time.Instant;
import java.util.Set;

/**
 * Persisted API key record.
 *
 * The raw secret is never stored — only {@link #keyHash} (SHA-256 hex) is.
 * Lookups happen by hash.
 */
public record ApiKey(
        String id,
        String keyHash,
        String principalId,
        String tier,
        Set<String> scopes,
        boolean enabled,
        Instant createdAt,
        Instant expiresAt) {

    public boolean isUsable(Instant now) {
        return enabled && (expiresAt == null || expiresAt.isAfter(now));
    }
}
