package com.mycompany.router.accesslog;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogPipelineTest {

    @Test
    void deliversEventsToSink() {
        List<AccessLogEvent> received = new CopyOnWriteArrayList<>();
        AccessLogSink sink = new AccessLogSink() {
            @Override public String name() { return "capture"; }
            @Override public void publish(AccessLogEvent event) { received.add(event); }
        };
        AccessLogPipeline pipeline = new AccessLogPipeline(100, sink, new SimpleMeterRegistry());

        pipeline.offer(event("a"));
        pipeline.offer(event("b"));

        Awaitility.await().atMost(Duration.ofSeconds(2))
                .until(() -> received.size() == 2);
        pipeline.shutdown();
    }

    @Test
    void dropsWhenQueueFull() {
        AccessLogSink slow = new AccessLogSink() {
            @Override public String name() { return "slow"; }
            @Override public void publish(AccessLogEvent event) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
        };
        AccessLogPipeline pipeline = new AccessLogPipeline(2, slow, new SimpleMeterRegistry());

        for (int i = 0; i < 50; i++) {
            pipeline.offer(event("e-" + i));
        }
        assertThat(pipeline.droppedCount()).isGreaterThan(0);
        pipeline.shutdown();
    }

    private AccessLogEvent event(String id) {
        return new AccessLogEvent(id, Instant.now(), "GET", "/x", "",
                "svc", "http://t", 200, 1, 0, 0,
                "p", "basic", "127.0.0.1", "test", "");
    }
}
