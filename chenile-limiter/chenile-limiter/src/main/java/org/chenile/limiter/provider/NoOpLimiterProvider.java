package org.chenile.limiter.provider;

import org.chenile.limiter.api.LimitResult;
import org.chenile.limiter.api.LimiterProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback used when no real provider is on the classpath. Allows everything and warns once at
 * startup.
 *
 * <p>It exists so the interceptor bean is always available: {@code chenile.pre.processors} resolves
 * bean names lazily on the first request, so omitting the bean would turn a missing provider into a
 * per-request failure instead of a startup warning.
 */
public class NoOpLimiterProvider implements LimiterProvider {

    private static final Logger logger = LoggerFactory.getLogger(NoOpLimiterProvider.class);

    public NoOpLimiterProvider() {
        logger.warn("No LimiterProvider was found, so @ChenileLimiter is NOT being enforced. Add "
                + "chenile-limiter-memory, chenile-limiter-redis or chenile-limiter-hazelcast, or "
                + "define your own LimiterProvider bean. If chenile.limiter.provider is set, check "
                + "it names a provider whose module is on the classpath.");
    }

    @Override
    public LimitResult tryAcquire(String key, int maxRequests, int windowSeconds) {
        return new LimitResult(true, maxRequests, maxRequests, windowSeconds);
    }
}
