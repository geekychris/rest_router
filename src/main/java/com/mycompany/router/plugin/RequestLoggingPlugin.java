package com.mycompany.router.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingPlugin implements RouterPlugin {
    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingPlugin.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public RequestLoggingPlugin(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Mono<ServerHttpRequest> preProcess(ServerHttpRequest request) {
        return Mono.fromCallable(() -> {
            String logMessage = String.format("Incoming request: %s %s",
                    request.getMethod(),
                    request.getURI());
            
            logger.info(logMessage);
            kafkaTemplate.send("request-logs", logMessage);
            
            return request;
        });
    }

    @Override
    public Mono<ServerHttpResponse> postProcess(ServerHttpResponse response) {
        return Mono.just(response);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
