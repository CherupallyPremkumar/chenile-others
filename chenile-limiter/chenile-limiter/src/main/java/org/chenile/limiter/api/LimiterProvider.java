package org.chenile.limiter.api;

/**
 * Storage engine for quota counters. Implementations must be atomic across every node sharing a
 * counter.
 */
public interface LimiterProvider {

    /**
     * @param key the bucket to charge, as produced by a {@link LimiterKeyResolver}
     * @param maxRequests quota for the window; always positive
     * @param windowSeconds window length; always positive
     */
    LimitResult tryAcquire(String key, int maxRequests, int windowSeconds);
}
