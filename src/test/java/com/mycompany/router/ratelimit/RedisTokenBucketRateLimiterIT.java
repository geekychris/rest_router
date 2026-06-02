package com.mycompany.router.ratelimit;

import com.redis.testcontainers.RedisContainer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spins up a real Redis via Testcontainers and exercises the Lua script.
 * Runs with {@code mvn -Dtest=RedisTokenBucketRateLimiterIT test} — skipped
 * automatically when Docker is unavailable on the runner.
 */
@Testcontainers
class RedisTokenBucketRateLimiterIT {

    static RedisContainer redis;
    static LettuceConnectionFactory factory;
    static ReactiveStringRedisTemplate template;

    @BeforeAll
    static void up() {
        redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));
        redis.start();
        factory = new LettuceConnectionFactory(redis.getRedisHost(), redis.getRedisPort());
        factory.afterPropertiesSet();
        template = new ReactiveStringRedisTemplate(factory);
    }

    @AfterAll
    static void down() {
        factory.destroy();
        redis.stop();
    }

    @Test
    void enforcesLimitAcrossKeys() {
        RedisTokenBucketRateLimiter rl = new RedisTokenBucketRateLimiter(template);
        for (int i = 0; i < 5; i++) {
            StepVerifier.create(rl.check("rl:it:userA", 5, Duration.ofMinutes(1)))
                    .assertNext(d -> assertThat(d.allowed()).isTrue())
                    .verifyComplete();
        }
        StepVerifier.create(rl.check("rl:it:userA", 5, Duration.ofMinutes(1)))
                .assertNext(d -> {
                    assertThat(d.allowed()).isFalse();
                    assertThat(d.retryAfterMs()).isGreaterThan(0);
                })
                .verifyComplete();
        StepVerifier.create(rl.check("rl:it:userB", 5, Duration.ofMinutes(1)))
                .assertNext(d -> assertThat(d.allowed()).isTrue())
                .verifyComplete();
    }
}
