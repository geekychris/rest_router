package com.mycompany.router.plugin;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

public interface RouterPlugin {
    Mono<ServerHttpRequest> preProcess(ServerHttpRequest request);
    Mono<ServerHttpResponse> postProcess(ServerHttpResponse response);
    int getOrder();
}
