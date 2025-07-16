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
        private int rateLimit;
        private String rateLimitPeriod = "MINUTE";
        private List<RouteConfig> routes;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getRateLimit() {
            return rateLimit;
        }

        public void setRateLimit(int rateLimit) {
            this.rateLimit = rateLimit;
        }

        public String getRateLimitPeriod() {
            return rateLimitPeriod;
        }

        public void setRateLimitPeriod(String rateLimitPeriod) {
            this.rateLimitPeriod = rateLimitPeriod;
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
