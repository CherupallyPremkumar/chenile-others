package org.chenile.limiter.memory.test.chain;

import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.ChenileExchangeBuilder;
import org.chenile.core.context.HeaderUtils;
import org.chenile.core.entrypoint.ChenileEntryPoint;
import org.chenile.core.model.ChenileConfiguration;
import org.chenile.core.model.OperationDefinition;
import org.chenile.limiter.annotation.ChenileLimiter;
import org.chenile.limiter.memory.test.StubLimit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Runs the limiter inside a real OWIZ chain, registered through {@code chenile.pre.processors}.
 *
 * <p>Confirms the OperationDefinition is already on the exchange at that seam — position 5, before
 * the body is transformed — and that refusing there stops the chain rather than merely recording an
 * error.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ChainSpringConfig.class)
public class TestPreProcessorPosition {

    private static final int QUOTA = 2;

    @Autowired private ChenileEntryPoint chenileEntryPoint;
    @Autowired private ChenileExchangeBuilder chenileExchangeBuilder;
    @Autowired private ChenileConfiguration chenileConfiguration;

    @Before
    public void attachQuotaToTheOperation() {
        CounterServiceImpl.INVOCATIONS.set(0);
        operationDefinition().putExtensionAsAnnotation(ChenileLimiter.class, new StubLimit(QUOTA, 60));
    }

    @Test
    public void theLimiterSeesTheOperationDefinitionAtThePreProcessorSeam() {
        ChenileExchange exchange = newExchange("sees-od");
        chenileEntryPoint.execute(exchange);

        assertNull(exchange.getException());
        assertEquals(1, CounterServiceImpl.INVOCATIONS.get());
    }

    @Test
    public void refusingAtThePreProcessorSeamStopsTheChainBeforeTheService() {
        for (int i = 0; i < QUOTA; i++) {
            chenileEntryPoint.execute(newExchange("stops-chain"));
        }
        assertEquals("the quota should have been spent", QUOTA, CounterServiceImpl.INVOCATIONS.get());

        ChenileExchange refused = newExchange("stops-chain");
        chenileEntryPoint.execute(refused);

        assertNotNull("the over-quota call must fail", refused.getException());
        assertEquals("service-invoker must never be reached once the limiter refuses",
                QUOTA, CounterServiceImpl.INVOCATIONS.get());
    }

    private OperationDefinition operationDefinition() {
        return chenileConfiguration.getServices().get("counterService").getOperations().stream()
                .filter(od -> od.getName().equals("hit"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("operation 'hit' was not registered"));
    }

    /** A tenant per test: the provider is a context-scoped singleton, so a shared key would leak. */
    private ChenileExchange newExchange(String tenant) {
        ChenileExchange exchange = chenileExchangeBuilder.makeExchange("counterService", "hit", null);
        if (exchange.getHeaders() == null) {
            exchange.setHeaders(new HashMap<>());
        }
        HeaderUtils.setTenant(exchange.getHeaders(), tenant);
        return exchange;
    }
}
