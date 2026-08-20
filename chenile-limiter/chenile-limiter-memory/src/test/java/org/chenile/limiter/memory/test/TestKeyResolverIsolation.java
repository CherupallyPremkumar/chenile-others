package org.chenile.limiter.memory.test;

import org.chenile.base.exception.ErrorNumException;
import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.HeaderUtils;
import org.chenile.core.model.OperationDefinition;
import org.chenile.limiter.annotation.ChenileLimiter;
import org.chenile.limiter.interceptor.LimiterInterceptor;
import org.chenile.limiter.memory.InMemoryLimiterProvider;
import org.chenile.limiter.resolver.CompositeKeyResolver;
import org.chenile.limiter.resolver.UserKeyResolver;
import org.chenile.owiz.impl.ChainContext;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.fail;

/** Different callers must not share a bucket. */
public class TestKeyResolverIsolation {

    @Test
    public void eachUserGetsTheirOwnQuota() throws Exception {
        LimiterInterceptor interceptor =
                new LimiterInterceptor(new InMemoryLimiterProvider(), new UserKeyResolver());

        ChenileExchange john = exchangeFor("user_john", null, "applyFilter", 2);
        ChenileExchange mary = exchangeFor("user_mary", null, "applyFilter", 2);

        interceptor.execute(john);
        interceptor.execute(john);
        assertRefused(interceptor, john);

        interceptor.execute(mary);
    }

    @Test
    public void tenantAndUserTogetherFormTheKey() throws Exception {
        LimiterInterceptor interceptor =
                new LimiterInterceptor(new InMemoryLimiterProvider(), new CompositeKeyResolver());

        ChenileExchange alpha = exchangeFor("user_101", "tenant_alpha", "applyFilter", 1);
        ChenileExchange beta = exchangeFor("user_101", "tenant_beta", "applyFilter", 1);

        interceptor.execute(alpha);
        assertRefused(interceptor, alpha);

        interceptor.execute(beta);
    }

    private void assertRefused(LimiterInterceptor interceptor, ChenileExchange exchange)
            throws Exception {
        try {
            interceptor.execute(exchange);
            fail("expected the caller to be over quota");
        } catch (ErrorNumException expected) {
            // refused as intended
        }
    }

    private ChenileExchange exchangeFor(String userId, String tenantId, String operationName,
                                        int maxRequests) {
        ChenileExchange exchange = new ChenileExchange();
        exchange.setHeaders(new HashMap<>());
        HeaderUtils.setUserId(exchange.getHeaders(), userId);
        if (tenantId != null) {
            HeaderUtils.setTenant(exchange.getHeaders(), tenantId);
        }

        OperationDefinition od = new OperationDefinition();
        od.setName(operationName);
        od.putExtensionAsAnnotation(ChenileLimiter.class, new StubLimit(maxRequests, 60));
        exchange.setOperationDefinition(od);
        exchange.setChainContext(new ChainContext<>(List.of(), exchange));
        return exchange;
    }
}
