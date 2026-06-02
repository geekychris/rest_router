package com.mycompany.router.accesslog;

import java.time.Instant;

/**
 * Single access-log record. Immutable, JSON-serialisable.
 *
 * Fields are deliberately flat (no nesting) so downstream consumers — Kafka,
 * S3, Athena, ClickHouse — can map them to columns trivially.
 */
public record AccessLogEvent(
        String requestId,
        Instant timestamp,
        String method,
        String path,
        String query,
        String service,
        String targetUrl,
        int status,
        long latencyMs,
        long requestBytes,
        long responseBytes,
        String principalId,
        String tier,
        String clientIp,
        String userAgent,
        String error) {
}
