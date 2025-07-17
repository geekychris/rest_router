package com.mycompany.router.routing;

import com.mycompany.router.config.RouterProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class HeaderBasedStrategy implements RouteSelectionStrategy {
    @Override
    public Optional<RouterProperties.RouteConfig> selectRoute(List<RouterProperties.RouteConfig> routes, ServerRequest request) {
        return routes.stream()
                .filter(route -> matchesRoute(route, request))
                .findFirst();
    }

    private boolean matchesRoute(RouterProperties.RouteConfig route, ServerRequest request) {
        if (!request.path().startsWith(route.getPath())) {
            return false;
        }

        // Check if all required headers are present with matching values
        Map<String, String> requiredHeaders = route.getHeaders();
        if (requiredHeaders != null && !requiredHeaders.isEmpty()) {
            return requiredHeaders.entrySet().stream()
                    .allMatch(entry -> 
                        request.headers()
                               .header(entry.getKey())
                               .contains(entry.getValue())
                    );
        }

        // If no headers are specified, this is a catch-all route
        return true;
    }
}
