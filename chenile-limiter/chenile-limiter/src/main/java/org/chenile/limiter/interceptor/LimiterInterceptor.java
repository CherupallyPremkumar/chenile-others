package org.chenile.limiter.interceptor;

import org.chenile.base.exception.ErrorNumException;
import org.chenile.core.context.ChenileExchange;
import org.chenile.core.interceptors.BaseChenileInterceptor;
import org.chenile.limiter.annotation.ChenileLimiter;
import org.chenile.limiter.api.LimitResult;
import org.chenile.limiter.api.LimiterKeyResolver;
import org.chenile.limiter.api.LimiterProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces {@link ChenileLimiter} on the Chenile interceptor chain.
 *
 * <p>Inert until a deployment registers it, with {@code @InterceptedBy("limiterInterceptor")} on an
 * operation or by naming it in {@code chenile.pre.processors}.
 */
public class LimiterInterceptor extends BaseChenileInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LimiterInterceptor.class);

    private static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    private static final String INVALID_QUOTA = "INVALID_RATE_LIMIT_QUOTA";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR = 500;

    private final LimiterProvider limiterProvider;
    private final LimiterKeyResolver keyResolver;

    public LimiterInterceptor(LimiterProvider limiterProvider, LimiterKeyResolver keyResolver) {
        this.limiterProvider = limiterProvider;
        this.keyResolver = keyResolver;
    }

    @Override
    protected boolean bypassInterception(ChenileExchange exchange) {
        return getExtensionByAnnotation(ChenileLimiter.class, exchange) == null;
    }

    @Override
    protected void doPreProcessing(ChenileExchange exchange) {
        ChenileLimiter limit = getExtensionByAnnotation(ChenileLimiter.class, exchange);
        if (limit == null) {
            return;
        }
        validate(limit);

        String key = keyResolver.resolveKey(exchange);
        LimitResult result = limiterProvider.tryAcquire(key, limit.maxRequests(), limit.windowSeconds());

        if (!result.allowed()) {
            logger.warn("Rate limit exceeded for {}. Quota: {} req / {}s", key, result.limit(),
                    limit.windowSeconds());
            throw new ErrorNumException(TOO_MANY_REQUESTS, RATE_LIMIT_EXCEEDED, message(limit));
        }
    }

    /**
     * A non-positive window makes the Redis script issue {@code EXPIRE key 0}, which deletes the key
     * and resets the counter on every call, so the quota is never enforced. Fail loudly instead.
     */
    private void validate(ChenileLimiter limit) {
        if (limit.maxRequests() <= 0 || limit.windowSeconds() <= 0) {
            throw new ErrorNumException(SERVER_ERROR, INVALID_QUOTA,
                    "@ChenileLimiter requires positive values. Got maxRequests="
                            + limit.maxRequests() + ", windowSeconds=" + limit.windowSeconds());
        }
    }

    private String message(ChenileLimiter limit) {
        return "Rate limit exceeded. Allowed: " + limit.maxRequests() + " requests per "
                + limit.windowSeconds() + " seconds.";
    }
}
