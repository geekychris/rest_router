package com.mycompany.router.accesslog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publishes events to a Kafka topic. Keying by {@code principalId} gives
 * per-principal ordering and even-ish partition distribution.
 */
public class KafkaSink implements AccessLogSink {

    private static final Logger log = LoggerFactory.getLogger(KafkaSink.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaSink(KafkaTemplate<String, String> kafka, ObjectMapper mapper, String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    @Override
    public String name() {
        return "kafka";
    }

    @Override
    public void publish(AccessLogEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            kafka.send(topic, event.principalId(), json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise access log event", e);
        }
    }

    @Override
    public void close() {
        kafka.flush();
    }
}
