package com.ratelimiter.config;

import com.ratelimiter.repository.RedisRateLimiterRepository;
import com.ratelimiter.service.algorithm.SlidingWindowCounterAlgorithm;
import com.ratelimiter.service.algorithm.TokenBucketAlgorithm;
import com.ratelimiter.time.MonotonicUtcClock;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterConfiguration {

    @Bean
    public Clock rateLimiterClock() {
        return new MonotonicUtcClock();
    }

    @Bean
    public TokenBucketAlgorithm tokenBucketAlgorithm(RedisRateLimiterRepository repository, Clock rateLimiterClock) {
        return new TokenBucketAlgorithm(repository, rateLimiterClock);
    }

    @Bean
    public SlidingWindowCounterAlgorithm slidingWindowCounterAlgorithm(RedisRateLimiterRepository repository, Clock rateLimiterClock) {
        return new SlidingWindowCounterAlgorithm(repository, rateLimiterClock);
    }

    @Bean
    public CircuitBreakerConfigCustomizer redisRateLimiterCircuitBreaker() {
        return CircuitBreakerConfigCustomizer.of(
                "redisRateLimiter",
                builder -> builder
                        .failureRateThreshold(50.0f)
                        .slidingWindowSize(20)
                        .waitDurationInOpenState(java.time.Duration.ofSeconds(10))
                        .minimumNumberOfCalls(10)
                        .permittedNumberOfCallsInHalfOpenState(3)
        );
    }
}
