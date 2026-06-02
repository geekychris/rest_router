package com.mycompany.router.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "router")
public class RouterProperties {

    private Map<String, ServiceConfig> services = new HashMap<>();
    private AuthConfig auth = new AuthConfig();
    private RateLimitsConfig rateLimits = new RateLimitsConfig();
    private AccessLogConfig accessLog = new AccessLogConfig();
    private AdminConfig admin = new AdminConfig();

    public Map<String, ServiceConfig> getServices() { return services; }
    public void setServices(Map<String, ServiceConfig> services) { this.services = services; }

    public AuthConfig getAuth() { return auth; }
    public void setAuth(AuthConfig auth) { this.auth = auth; }

    public RateLimitsConfig getRateLimits() { return rateLimits; }
    public void setRateLimits(RateLimitsConfig rateLimits) { this.rateLimits = rateLimits; }

    public AccessLogConfig getAccessLog() { return accessLog; }
    public void setAccessLog(AccessLogConfig accessLog) { this.accessLog = accessLog; }

    public AdminConfig getAdmin() { return admin; }
    public void setAdmin(AdminConfig admin) { this.admin = admin; }

    // ===== Service =====
    public static class ServiceConfig {
        private String baseUrl;
        private RateLimitConfig defaultRateLimit;
        private Map<String, RateLimitConfig> clientRateLimits = new HashMap<>();
        /** @deprecated kept for backward compatibility; prefer auth via API key. */
        @Deprecated
        private String clientIdHeader = "X-Client-Id";
        private boolean requireAuth = false;
        private List<RouteConfig> routes = new ArrayList<>();

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public RateLimitConfig getDefaultRateLimit() { return defaultRateLimit; }
        public void setDefaultRateLimit(RateLimitConfig defaultRateLimit) { this.defaultRateLimit = defaultRateLimit; }

        public Map<String, RateLimitConfig> getClientRateLimits() { return clientRateLimits; }
        public void setClientRateLimits(Map<String, RateLimitConfig> clientRateLimits) { this.clientRateLimits = clientRateLimits; }

        public String getClientIdHeader() { return clientIdHeader; }
        public void setClientIdHeader(String clientIdHeader) { this.clientIdHeader = clientIdHeader; }

        public boolean isRequireAuth() { return requireAuth; }
        public void setRequireAuth(boolean requireAuth) { this.requireAuth = requireAuth; }

        public List<RouteConfig> getRoutes() { return routes; }
        public void setRoutes(List<RouteConfig> routes) { this.routes = routes; }
    }

    public static class RouteConfig {
        private String path;
        private Map<String, String> headers = new HashMap<>();
        private Map<String, String> params = new HashMap<>();
        private String targetUrl;
        private int weight = 100;
        /** Strip this prefix from the inbound path before forwarding. Empty = forward as-is. */
        private String stripPrefix = "";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }

        public Map<String, String> getParams() { return params; }
        public void setParams(Map<String, String> params) { this.params = params; }

        public String getTargetUrl() { return targetUrl; }
        public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }

        public String getStripPrefix() { return stripPrefix; }
        public void setStripPrefix(String stripPrefix) { this.stripPrefix = stripPrefix; }
    }

    // ===== Auth =====
    public static class AuthConfig {
        private boolean enabled = true;
        private String apiKeyHeader = "X-API-Key";
        private String storage = "in-memory"; // in-memory | redis
        private List<BootstrapApiKey> bootstrapKeys = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }

        public String getStorage() { return storage; }
        public void setStorage(String storage) { this.storage = storage; }

        public List<BootstrapApiKey> getBootstrapKeys() { return bootstrapKeys; }
        public void setBootstrapKeys(List<BootstrapApiKey> bootstrapKeys) { this.bootstrapKeys = bootstrapKeys; }
    }

    public static class BootstrapApiKey {
        private String id;
        private String key;
        private String principalId;
        private String tier = "basic";
        private List<String> scopes = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getPrincipalId() { return principalId; }
        public void setPrincipalId(String principalId) { this.principalId = principalId; }

        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }

        public List<String> getScopes() { return scopes; }
        public void setScopes(List<String> scopes) { this.scopes = scopes; }
    }

    // ===== Rate limits =====
    public static class RateLimitsConfig {
        private String backend = "redis"; // redis | local
        private Map<String, RateLimitConfig> tiers = new HashMap<>();

        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }

        public Map<String, RateLimitConfig> getTiers() { return tiers; }
        public void setTiers(Map<String, RateLimitConfig> tiers) { this.tiers = tiers; }
    }

    // ===== Access log =====
    public static class AccessLogConfig {
        private boolean enabled = true;
        private int queueCapacity = 10_000;
        private String sink = "stdout"; // kafka | stdout | file | noop
        private KafkaSinkConfig kafka = new KafkaSinkConfig();
        private FileSinkConfig file = new FileSinkConfig();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        public String getSink() { return sink; }
        public void setSink(String sink) { this.sink = sink; }

        public KafkaSinkConfig getKafka() { return kafka; }
        public void setKafka(KafkaSinkConfig kafka) { this.kafka = kafka; }

        public FileSinkConfig getFile() { return file; }
        public void setFile(FileSinkConfig file) { this.file = file; }
    }

    public static class KafkaSinkConfig {
        private String topic = "gateway-access-log";

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }

    public static class FileSinkConfig {
        private String path = "/var/log/gateway/access.log";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    // ===== Admin =====
    public static class AdminConfig {
        private boolean enabled = true;
        private String apiKey = "changeme";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
