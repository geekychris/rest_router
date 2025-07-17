package com.mycompany.router.service;

import com.mycompany.router.config.RouterProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ServiceRegistry {
    private final ConcurrentHashMap<String, RouterProperties.ServiceConfig> services;
    private final RateLimiterService rateLimiterService;

    public ServiceRegistry(RateLimiterService rateLimiterService) {
        this.services = new ConcurrentHashMap<>();
        this.rateLimiterService = rateLimiterService;
    }

    public void registerService(String serviceName, RouterProperties.ServiceConfig serviceConfig) {
        services.put(serviceName, serviceConfig);
        rateLimiterService.updateRateLimiter(serviceName, serviceConfig);
    }

    public void updateService(String serviceName, RouterProperties.ServiceConfig serviceConfig) {
        services.put(serviceName, serviceConfig);
        rateLimiterService.updateRateLimiter(serviceName, serviceConfig);
    }

    public void removeService(String serviceName) {
        RouterProperties.ServiceConfig config = services.get(serviceName);
        if (config != null) {
            rateLimiterService.updateRateLimiter(serviceName, config);
        }
        services.remove(serviceName);
    }

    public RouterProperties.ServiceConfig getService(String serviceName) {
        return services.get(serviceName);
    }

    public Map<String, RouterProperties.ServiceConfig> getAllServices() {
        return Map.copyOf(services);
    }
}
