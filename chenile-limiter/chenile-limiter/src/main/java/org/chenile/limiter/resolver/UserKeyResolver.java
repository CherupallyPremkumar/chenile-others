package org.chenile.limiter.resolver;

import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.HeaderUtils;
import org.chenile.limiter.api.LimiterKeyResolver;

/**
 * One quota per user per operation, ignoring tenant.
 *
 * <p>See the header-trust note on {@link CompositeKeyResolver}, which applies here too.
 */
public class UserKeyResolver implements LimiterKeyResolver {

    @Override
    public String resolveKey(ChenileExchange exchange) {
        String userId = HeaderUtils.getUserId(exchange.getHeaders());
        if (userId == null || userId.isBlank()) {
            userId = "ANONYMOUS_USER";
        }
        String opName = exchange.getOperationDefinition() != null
                ? exchange.getOperationDefinition().getName() : "default_operation";
        return "user:" + userId + ":" + opName;
    }
}
