package org.chenile.limiter.api;

import org.chenile.core.context.ChenileExchange;

/** Decides which quota bucket a request belongs to. */
@FunctionalInterface
public interface LimiterKeyResolver {

    /** @return the bucket key; never null */
    String resolveKey(ChenileExchange exchange);
}
