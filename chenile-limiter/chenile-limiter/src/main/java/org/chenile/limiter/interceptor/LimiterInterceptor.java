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

    /**
     * Response headers. The de-facto {@code X-RateLimit-*} trio tells a well-behaved client how much
     * quota it has left before it has to back off, and {@code Retry-After} (RFC 9110) tells a
     * throttled one how long to wait. Emitting them turns a 429 from a dead end into something a
     * client can pace against, which is what keeps a busy fleet from hammering a limit in a tight
     * retry loop.
     */
    private static final String HDR_LIMIT = "X-RateLimit-Limit";
    private static final String HDR_REMAINING = "X-RateLimit-Remaining";
    private static final String HDR_RESET = "X-RateLimit-Reset";
    private static final String HDR_RETRY_AFTER = "Retry-After";

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

        // Set on every call, allowed or not: a client learns its standing from a successful response
        // just as much as from a rejected one. These are three map puts on the hot path, no allocation
        // beyond the integer-to-string conversions, so instrumenting every request stays cheap.
        setRateLimitHeaders(exchange, result);

        if (!result.allowed()) {
            exchange.setHeader(HDR_RETRY_AFTER, Long.toString(Math.max(0L, result.resetSeconds())));
            logger.warn("Rate limit exceeded for {}. Quota: {} req / {}s", key, result.limit(),
                    limit.windowSeconds());
            throw new ErrorNumException(TOO_MANY_REQUESTS, RATE_LIMIT_EXCEEDED, message(limit));
        }
    }

    /**
     * Publishes the current quota standing as response headers. {@code remaining} is floored at zero:
     * a provider may report a negative count when a burst overshoots, but a client should never be
     * told it has less than no quota.
     */
    private void setRateLimitHeaders(ChenileExchange exchange, LimitResult result) {
        exchange.setHeader(HDR_LIMIT, Integer.toString(result.limit()));
        exchange.setHeader(HDR_REMAINING, Integer.toString(Math.max(0, result.remaining())));
        exchange.setHeader(HDR_RESET, Long.toString(Math.max(0L, result.resetSeconds())));
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
