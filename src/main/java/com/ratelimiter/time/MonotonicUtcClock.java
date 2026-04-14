package com.ratelimiter.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Monotonic UTC clock derived from System.nanoTime().
 *
 * This prevents wall-clock jumps (e.g. date -s / NTP step adjustments) from
 * directly affecting rate-limit math, while still producing UTC Instants.
 */
public class MonotonicUtcClock extends Clock {

    private final long baseEpochMs;
    private final long baseNanoTime;

    public MonotonicUtcClock() {
        this.baseEpochMs = System.currentTimeMillis();
        this.baseNanoTime = System.nanoTime();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        // Keep UTC behavior stable for limiter math.
        return this;
    }

    @Override
    public Instant instant() {
        long elapsedMs = (System.nanoTime() - baseNanoTime) / 1_000_000L;
        return Instant.ofEpochMilli(baseEpochMs + elapsedMs);
    }
}
