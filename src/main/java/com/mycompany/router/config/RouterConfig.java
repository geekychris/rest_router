package com.mycompany.router.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mycompany.router.accesslog.AccessLogPipeline;
import com.mycompany.router.accesslog.AccessLogSink;
import com.mycompany.router.accesslog.FileSink;
import com.mycompany.router.accesslog.KafkaSink;
import com.mycompany.router.accesslog.NoopSink;
import com.mycompany.router.accesslog.StdoutJsonSink;
import com.mycompany.router.admin.AdminAuthFilter;
import com.mycompany.router.auth.ApiKey;
import com.mycompany.router.auth.ApiKeyAuthFilter;
import com.mycompany.router.auth.ApiKeyHasher;
import com.mycompany.router.auth.ApiKeyStore;
import com.mycompany.router.auth.InMemoryApiKeyStore;
import com.mycompany.router.auth.RedisApiKeyStore;
import com.mycompany.router.handler.RouterHandler;
import com.mycompany.router.ratelimit.LocalTokenBucketRateLimiter;
import com.mycompany.router.ratelimit.RateLimiter;
import com.mycompany.router.ratelimit.RedisTokenBucketRateLimiter;
import com.mycompany.router.routing.HeaderBasedStrategy;
import com.mycompany.router.routing.RouteSelectionStrategy;
import com.mycompany.router.routing.WeightedTrafficStrategy;
import com.mycompany.router.service.ServiceRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterConfig {

    private static final Logger log = LoggerFactory.getLogger(RouterConfig.class);

    private final RouterProperties properties;

    public RouterConfig(RouterProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RouterFunction<ServerResponse> routerFunction(RouterHandler handler) {
        return route(all(), handler::handleRequest);
    }

    @Bean
    public List<RouteSelectionStrategy> routingStrategies(HeaderBasedStrategy headerBasedStrategy,
                                                          WeightedTrafficStrategy weightedTrafficStrategy) {
        return List.of(headerBasedStrategy, weightedTrafficStrategy);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    // ===== Service registry =====
    @Bean
    public ServiceRegistry serviceRegistry() {
        ServiceRegistry registry = new ServiceRegistry();
        properties.getServices().forEach(registry::registerService);
        return registry;
    }

    // ===== Auth =====
    @Bean
    public ApiKeyStore apiKeyStore(Optional<ReactiveStringRedisTemplate> redis, ObjectMapper mapper) {
        String storage = properties.getAuth().getStorage();
        if ("redis".equalsIgnoreCase(storage) && redis.isPresent()) {
            log.info("API key storage: Redis");
            return new RedisApiKeyStore(redis.get(), mapper);
        }
        log.info("API key storage: in-memory");
        return new InMemoryApiKeyStore();
    }

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter(ApiKeyStore store) {
        return new ApiKeyAuthFilter(store, properties.getAuth());
    }

    @Bean
    public AdminAuthFilter adminAuthFilter() {
        return new AdminAuthFilter(properties.getAdmin());
    }

    @Bean
    public BootstrapKeysLoader bootstrapKeysLoader(ApiKeyStore store) {
        return new BootstrapKeysLoader(store, properties);
    }

    // ===== Rate limiter =====
    @Bean
    public RateLimiter rateLimiter(Optional<ReactiveStringRedisTemplate> redis) {
        String backend = properties.getRateLimits().getBackend();
        if ("redis".equalsIgnoreCase(backend) && redis.isPresent()) {
            log.info("Rate limiter backend: Redis token bucket");
            return new RedisTokenBucketRateLimiter(redis.get());
        }
        log.info("Rate limiter backend: local token bucket");
        return new LocalTokenBucketRateLimiter();
    }

    // ===== Access log =====
    @Bean
    public AccessLogSink accessLogSink(ObjectMapper mapper,
                                       Optional<KafkaTemplate<String, String>> kafka) throws IOException {
        if (!properties.getAccessLog().isEnabled()) {
            log.info("Access log disabled");
            return new NoopSink();
        }
        String sink = properties.getAccessLog().getSink();
        return switch (sink == null ? "stdout" : sink.toLowerCase()) {
            case "kafka" -> {
                if (kafka.isEmpty()) {
                    log.warn("Kafka sink requested but no KafkaTemplate bean — falling back to stdout");
                    yield new StdoutJsonSink(mapper);
                }
                yield new KafkaSink(kafka.get(), mapper, properties.getAccessLog().getKafka().getTopic());
            }
            case "file" -> new FileSink(mapper, properties.getAccessLog().getFile().getPath());
            case "noop" -> new NoopSink();
            default -> new StdoutJsonSink(mapper);
        };
    }

    @Bean
    public AccessLogPipeline accessLogPipeline(AccessLogSink sink, MeterRegistry registry) {
        return new AccessLogPipeline(properties.getAccessLog().getQueueCapacity(), sink, registry);
    }

    /**
     * Seeds the ApiKeyStore from {@code router.auth.bootstrapKeys} on startup.
     * Idempotent — re-running with the same keys produces the same hashes.
     */
    public static final class BootstrapKeysLoader {
        private final ApiKeyStore store;
        private final RouterProperties props;

        BootstrapKeysLoader(ApiKeyStore store, RouterProperties props) {
            this.store = store;
            this.props = props;
        }

        @PostConstruct
        void load() {
            for (RouterProperties.BootstrapApiKey b : props.getAuth().getBootstrapKeys()) {
                if (b.getKey() == null || b.getKey().isBlank()) continue;
                String hash = ApiKeyHasher.sha256Hex(b.getKey());
                ApiKey k = new ApiKey(
                        b.getId(),
                        hash,
                        b.getPrincipalId(),
                        b.getTier(),
                        new HashSet<>(b.getScopes()),
                        true,
                        Instant.now(),
                        null);
                store.save(k).subscribe();
            }
        }
    }
}
