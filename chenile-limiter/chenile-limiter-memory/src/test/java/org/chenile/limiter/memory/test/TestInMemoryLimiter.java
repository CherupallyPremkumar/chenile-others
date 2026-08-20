package org.chenile.limiter.memory.test;

import org.chenile.base.exception.ErrorNumException;
import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.HeaderUtils;
import org.chenile.core.model.OperationDefinition;
import org.chenile.limiter.annotation.ChenileLimiter;
import org.chenile.limiter.interceptor.LimiterInterceptor;
import org.chenile.limiter.memory.InMemoryLimiterProvider;
import org.chenile.limiter.resolver.TenantKeyResolver;
import org.chenile.owiz.impl.ChainContext;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestInMemoryLimiter {

    private LimiterInterceptor interceptor;

    @Before
    public void setUp() {
        interceptor = new LimiterInterceptor(new InMemoryLimiterProvider(), new TenantKeyResolver());
    }

    @Test
    public void underQuotaPassesAndOverQuotaIsRefused() throws Exception {
        ChenileExchange exchange = exchangeFor("tenant1", "getDetails", 3, 60);

        for (int i = 0; i < 3; i++) {
            interceptor.execute(exchange);
        }

        try {
            interceptor.execute(exchange);
            fail("the fourth request is over quota");
        } catch (ErrorNumException e) {
            assertEquals(429, e.getErrorNum());
            assertEquals("RATE_LIMIT_EXCEEDED", e.getSubErrorNum());
        }
    }

    @Test
    public void theWindowExpiresAndTheCallerRecovers() throws Exception {
        ChenileExchange exchange = exchangeFor("tenant1", "fastReset", 2, 1);

        interceptor.execute(exchange);
        interceptor.execute(exchange);
        try {
            interceptor.execute(exchange);
            fail("should be refused inside the window");
        } catch (ErrorNumException expected) {
            // refused as intended
        }

        Thread.sleep(1100);

        interceptor.execute(exchange);
    }

    /** A non-positive window makes Redis EXPIRE delete the key, so it must never reach a provider. */
    @Test
    public void anImpossibleQuotaIsRejectedRatherThanSilentlyIgnored() {
        ChenileExchange exchange = exchangeFor("tenant1", "broken", 5, 0);

        try {
            interceptor.execute(exchange);
            fail("windowSeconds=0 must be rejected");
        } catch (Exception e) {
            assertEquals(ErrorNumException.class, e.getClass());
            assertEquals(500, ((ErrorNumException) e).getErrorNum());
        }
    }

    private ChenileExchange exchangeFor(String tenantId, String operationName, int maxRequests,
                                        int windowSeconds) {
        ChenileExchange exchange = new ChenileExchange();
        exchange.setHeaders(new HashMap<>());
        HeaderUtils.setTenant(exchange.getHeaders(), tenantId);

        OperationDefinition od = new OperationDefinition();
        od.setName(operationName);
        od.putExtensionAsAnnotation(ChenileLimiter.class, new StubLimit(maxRequests, windowSeconds));
        exchange.setOperationDefinition(od);

        // Empty downstream chain: including the interceptor itself would charge two tokens per call.
        exchange.setChainContext(new ChainContext<>(List.of(), exchange));
        return exchange;
    }
}
