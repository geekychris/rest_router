package com.mycompany.router.routing;

import com.mycompany.router.config.RouterProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Component
public class WeightedTrafficStrategy implements RouteSelectionStrategy {
    private final Random random = new Random();

    @Override
    public Optional<RouterProperties.RouteConfig> selectRoute(List<RouterProperties.RouteConfig> routes, ServerRequest request) {
        if (routes.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = routes.stream()
                .filter(route -> matchesRoute(route, request))
                .mapToInt(RouterProperties.RouteConfig::getWeight)
                .sum();

        if (totalWeight == 0) {
            return Optional.empty();
        }

        int selection = random.nextInt(totalWeight);
        int currentWeight = 0;

        List<RouterProperties.RouteConfig> matchingRoutes = routes.stream()
                .filter(route -> matchesRoute(route, request))
                .toList();

        for (RouterProperties.RouteConfig route : matchingRoutes) {
            currentWeight += route.getWeight();
            if (selection < currentWeight) {
                return Optional.of(route);
            }
        }

        return Optional.of(matchingRoutes.get(0));
    }

    private boolean matchesRoute(RouterProperties.RouteConfig route, ServerRequest request) {
        return request.path().startsWith(route.getPath());
    }
}
