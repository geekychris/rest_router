package com.mycompany.router.routing;

import com.mycompany.router.config.RouterProperties;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.List;
import java.util.Optional;

public interface RouteSelectionStrategy {
    Optional<RouterProperties.RouteConfig> selectRoute(List<RouterProperties.RouteConfig> routes, ServerRequest request);
}
