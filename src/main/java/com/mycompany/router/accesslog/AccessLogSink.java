package com.mycompany.router.accesslog;

/**
 * Destination for access-log events.
 *
 * Extension point: register your own {@code AccessLogSink} bean and set
 * {@code router.accessLog.sink} to your sink's {@link #name()} (or wire it
 * directly in a configuration class).
 *
 * Implementations MUST be thread-safe and SHOULD NOT block longer than a few
 * milliseconds — the pipeline dispatcher is a single thread.
 */
public interface AccessLogSink {

    String name();

    void publish(AccessLogEvent event);

    default void close() {}
}
