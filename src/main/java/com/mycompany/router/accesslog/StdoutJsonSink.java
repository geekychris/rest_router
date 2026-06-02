package com.mycompany.router.accesslog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StdoutJsonSink implements AccessLogSink {

    private static final Logger log = LoggerFactory.getLogger("access-log");

    private final ObjectMapper mapper;

    public StdoutJsonSink(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "stdout";
    }

    @Override
    public void publish(AccessLogEvent event) {
        try {
            log.info(mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise access log event", e);
        }
    }
}
