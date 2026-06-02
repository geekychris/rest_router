package com.mycompany.router.service;

import com.mycompany.router.config.RouterProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ServiceRegistry {
    private final ConcurrentHashMap<String, RouterProperties.ServiceConfig> services = new ConcurrentHashMap<>();

    public void registerService(String serviceName, RouterProperties.ServiceConfig serviceConfig) {
        services.put(serviceName, serviceConfig);
    }

    public void updateService(String serviceName, RouterProperties.ServiceConfig serviceConfig) {
        services.put(serviceName, serviceConfig);
    }

    public void removeService(String serviceName) {
        services.remove(serviceName);
    }

    public RouterProperties.ServiceConfig getService(String serviceName) {
        return services.get(serviceName);
    }

    public Map<String, RouterProperties.ServiceConfig> getAllServices() {
        return Map.copyOf(services);
    }
}
