package com.mycompany.router.ratelimit;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTokenBucketRateLimiterTest {

    @Test
    void allowsRequestsWithinLimit() {
        LocalTokenBucketRateLimiter rl = new LocalTokenBucketRateLimiter();
        for (int i = 0; i < 5; i++) {
            StepVerifier.create(rl.check("k", 5, Duration.ofSeconds(1)))
                    .assertNext(d -> assertThat(d.allowed()).isTrue())
                    .verifyComplete();
        }
    }

    @Test
    void deniesOnceBucketEmpty() {
        LocalTokenBucketRateLimiter rl = new LocalTokenBucketRateLimiter();
        for (int i = 0; i < 3; i++) {
            rl.check("user-a", 3, Duration.ofMinutes(1)).block();
        }
        StepVerifier.create(rl.check("user-a", 3, Duration.ofMinutes(1)))
                .assertNext(d -> {
                    assertThat(d.allowed()).isFalse();
                    assertThat(d.retryAfterMs()).isGreaterThan(0);
                    assertThat(d.limit()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    void keysAreIsolated() {
        LocalTokenBucketRateLimiter rl = new LocalTokenBucketRateLimiter();
        for (int i = 0; i < 2; i++) {
            rl.check("a", 2, Duration.ofMinutes(1)).block();
        }
        StepVerifier.create(rl.check("a", 2, Duration.ofMinutes(1)))
                .assertNext(d -> assertThat(d.allowed()).isFalse())
                .verifyComplete();
        StepVerifier.create(rl.check("b", 2, Duration.ofMinutes(1)))
                .assertNext(d -> assertThat(d.allowed()).isTrue())
                .verifyComplete();
    }
}
