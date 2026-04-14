package com.ratelimiter.service.algorithm;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RedisRateLimiterRepository;
import java.time.Clock;
import java.time.Instant;

public class SlidingWindowCounterAlgorithm implements RateLimitAlgorithm {

    private final RedisRateLimiterRepository rateLimiterRepository;
    private final Clock clock;

    public SlidingWindowCounterAlgorithm(RedisRateLimiterRepository rateLimiterRepository) {
        this(rateLimiterRepository, Clock.systemUTC());
    }

    public SlidingWindowCounterAlgorithm(RedisRateLimiterRepository rateLimiterRepository, Clock clock) {
        this.rateLimiterRepository = rateLimiterRepository;
        this.clock = clock;
    }

    @Override
    public boolean allowRequest(String key, RateLimitRule rule) {
        return rateLimiterRepository.allowSlidingWindowCounter(key, rule, Instant.now(clock));
    }

    @Override
    public AlgorithmType type() {
        return AlgorithmType.SLIDING_WINDOW_COUNTER;
    }
}
