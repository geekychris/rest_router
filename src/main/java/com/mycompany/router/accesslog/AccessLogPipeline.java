package com.mycompany.router.accesslog;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded queue + single dispatcher thread that drains events to a sink.
 *
 * Design choices:
 * <ul>
 *   <li><b>Bounded queue, drop-on-full</b>: hot path stays non-blocking even
 *       when the sink (e.g. Kafka) is slow. Drops are counted and exposed as
 *       a metric so loss is visible, not silent.</li>
 *   <li><b>Single dispatcher</b>: keeps publish ordering and avoids contention
 *       inside the sink (most sinks are happiest with one writer thread).</li>
 *   <li><b>Daemon thread</b>: does not block JVM shutdown.</li>
 * </ul>
 *
 * To increase throughput beyond what one dispatcher can sustain, partition by
 * principal id and run N pipelines (out of scope here).
 */
public class AccessLogPipeline {

    private static final Logger log = LoggerFactory.getLogger(AccessLogPipeline.class);

    private final BlockingQueue<AccessLogEvent> queue;
    private final AccessLogSink sink;
    private final Thread dispatcher;
    private final AtomicLong dropped = new AtomicLong();
    private final Counter publishedCounter;
    private final Counter droppedCounter;
    private volatile boolean running = true;

    public AccessLogPipeline(int capacity, AccessLogSink sink, MeterRegistry registry) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.sink = sink;
        this.publishedCounter = Counter.builder("gateway.access_log.published")
                .tag("sink", sink.name())
                .register(registry);
        this.droppedCounter = Counter.builder("gateway.access_log.dropped")
                .tag("sink", sink.name())
                .register(registry);
        this.dispatcher = new Thread(this::dispatch, "access-log-dispatcher");
        this.dispatcher.setDaemon(true);
        this.dispatcher.start();
        log.info("Access log pipeline started: sink={} capacity={}", sink.name(), capacity);
    }

    public void offer(AccessLogEvent event) {
        if (!queue.offer(event)) {
            dropped.incrementAndGet();
            droppedCounter.increment();
        }
    }

    public long droppedCount() {
        return dropped.get();
    }

    public int queueDepth() {
        return queue.size();
    }

    private void dispatch() {
        while (running || !queue.isEmpty()) {
            try {
                AccessLogEvent event = queue.poll(500, TimeUnit.MILLISECONDS);
                if (event == null) continue;
                try {
                    sink.publish(event);
                    publishedCounter.increment();
                } catch (Exception e) {
                    log.warn("Access log sink {} failed", sink.name(), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Draining access log pipeline ({} pending)", queue.size());
        running = false;
        try {
            dispatcher.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            sink.close();
        } catch (Exception e) {
            log.warn("Sink close failed", e);
        }
    }
}
