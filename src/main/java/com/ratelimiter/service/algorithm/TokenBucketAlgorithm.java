package com.ratelimiter.service.algorithm;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RedisRateLimiterRepository;
import java.time.Clock;
import java.time.Instant;

public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final RedisRateLimiterRepository rateLimiterRepository;
    private final Clock clock;

    public TokenBucketAlgorithm(RedisRateLimiterRepository rateLimiterRepository) {
        this(rateLimiterRepository, Clock.systemUTC());
    }

    public TokenBucketAlgorithm(RedisRateLimiterRepository rateLimiterRepository, Clock clock) {
        this.rateLimiterRepository = rateLimiterRepository;
        this.clock = clock;
    }

    @Override
    public boolean allowRequest(String key, RateLimitRule rule) {
        return rateLimiterRepository.allowTokenBucket(key, rule, Instant.now(clock));
    }

    @Override
    public AlgorithmType type() {
        return AlgorithmType.TOKEN_BUCKET;
    }
}
