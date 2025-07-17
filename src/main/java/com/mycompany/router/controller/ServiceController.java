package com.mycompany.router.controller;

import com.mycompany.router.config.RouterProperties;
import com.mycompany.router.service.ServiceRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/admin/services")
public class ServiceController {
    private final ServiceRegistry serviceRegistry;

    public ServiceController(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @GetMapping
    public Mono<Map<String, RouterProperties.ServiceConfig>> getAllServices() {
        return Mono.just(serviceRegistry.getAllServices());
    }

    @GetMapping("/{serviceName}")
    public Mono<ResponseEntity<RouterProperties.ServiceConfig>> getService(@PathVariable String serviceName) {
        RouterProperties.ServiceConfig config = serviceRegistry.getService(serviceName);
        if (config == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.ok(config));
    }

    @PostMapping("/{serviceName}")
    public Mono<ResponseEntity<Void>> registerService(
            @PathVariable String serviceName,
            @RequestBody RouterProperties.ServiceConfig config) {
        serviceRegistry.registerService(serviceName, config);
        return Mono.just(ResponseEntity.ok().build());
    }

    @PutMapping("/{serviceName}")
    public Mono<ResponseEntity<Void>> updateService(
            @PathVariable String serviceName,
            @RequestBody RouterProperties.ServiceConfig config) {
        if (serviceRegistry.getService(serviceName) == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        serviceRegistry.updateService(serviceName, config);
        return Mono.just(ResponseEntity.ok().build());
    }

    @DeleteMapping("/{serviceName}")
    public Mono<ResponseEntity<Void>> removeService(@PathVariable String serviceName) {
        if (serviceRegistry.getService(serviceName) == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        serviceRegistry.removeService(serviceName);
        return Mono.just(ResponseEntity.ok().build());
    }
}
