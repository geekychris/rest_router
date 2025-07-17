package com.mycompany.router.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "router")
public class RouterProperties {
    private Map<String, ServiceConfig> services = new HashMap<>();

    public Map<String, ServiceConfig> getServices() {
        return services;
    }

    public void setServices(Map<String, ServiceConfig> services) {
        this.services = services;
    }

public static class ServiceConfig {
        private String baseUrl;
        private RateLimitConfig defaultRateLimit;
        private Map<String, RateLimitConfig> clientRateLimits = new HashMap<>();
        private String clientIdHeader = "X-Client-Id";
        private List<RouteConfig> routes;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public RateLimitConfig getDefaultRateLimit() {
            return defaultRateLimit;
        }

        public void setDefaultRateLimit(RateLimitConfig defaultRateLimit) {
            this.defaultRateLimit = defaultRateLimit;
        }

        public Map<String, RateLimitConfig> getClientRateLimits() {
            return clientRateLimits;
        }

        public void setClientRateLimits(Map<String, RateLimitConfig> clientRateLimits) {
            this.clientRateLimits = clientRateLimits;
        }

        public String getClientIdHeader() {
            return clientIdHeader;
        }

        public void setClientIdHeader(String clientIdHeader) {
            this.clientIdHeader = clientIdHeader;
        }

        public List<RouteConfig> getRoutes() {
            return routes;
        }

        public void setRoutes(List<RouteConfig> routes) {
            this.routes = routes;
        }
    }

    public static class RouteConfig {
        private String path;
        private Map<String, String> headers = new HashMap<>();
        private Map<String, String> params = new HashMap<>();
        private String targetUrl;
        private int weight = 100;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public Map<String, String> getParams() {
            return params;
        }

        public void setParams(Map<String, String> params) {
            this.params = params;
        }

        public String getTargetUrl() {
            return targetUrl;
        }

        public void setTargetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }
    }
}
