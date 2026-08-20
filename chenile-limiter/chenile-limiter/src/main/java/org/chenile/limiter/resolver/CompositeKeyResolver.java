package org.chenile.limiter.resolver;

import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.HeaderUtils;
import org.chenile.limiter.api.LimiterKeyResolver;

/**
 * Quota per user, scoped by tenant and operation.
 *
 * <p>Identity comes from Chenile headers, which arrive from the caller on the HTTP path and are not
 * verified: only the {@code x-p-} prefix is protected. A deployment must own these headers at the
 * edge, or a client can mint fresh quotas by varying one string.
 */
public class CompositeKeyResolver implements LimiterKeyResolver {

    @Override
    public String resolveKey(ChenileExchange exchange) {
        String tenantId = HeaderUtils.getTenant(exchange.getHeaders());
        if (tenantId == null || tenantId.isBlank()) tenantId = "GLOBAL";

        String userId = HeaderUtils.getUserId(exchange.getHeaders());
        if (userId == null || userId.isBlank()) userId = "ANONYMOUS";

        String opName = exchange.getOperationDefinition() != null
                ? exchange.getOperationDefinition().getName() : "default_operation";

        return "composite:" + tenantId + ":" + userId + ":" + opName;
    }
}
