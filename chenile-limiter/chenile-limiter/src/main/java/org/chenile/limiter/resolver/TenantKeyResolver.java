package org.chenile.limiter.resolver;

import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.HeaderUtils;
import org.chenile.limiter.api.LimiterKeyResolver;

/**
 * One quota per tenant per operation, shared by every user of that tenant.
 *
 * <p>See the header-trust note on {@link CompositeKeyResolver}, which applies here too.
 */
public class TenantKeyResolver implements LimiterKeyResolver {

    @Override
    public String resolveKey(ChenileExchange exchange) {
        String tenantId = HeaderUtils.getTenant(exchange.getHeaders());
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "DEFAULT_TENANT";
        }
        String opName = exchange.getOperationDefinition() != null
                ? exchange.getOperationDefinition().getName() : "default_operation";
        return "tenant:" + tenantId + ":" + opName;
    }
}
