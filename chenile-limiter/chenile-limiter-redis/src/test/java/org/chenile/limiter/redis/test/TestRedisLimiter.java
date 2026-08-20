package org.chenile.limiter.redis.test;

import org.chenile.base.exception.ErrorNumException;
import org.chenile.limiter.api.LimitResult;
import org.chenile.limiter.redis.RedisLimiterProvider;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Covers how the provider interprets a script reply. The reply comes from a stub rather than a
 * server, so the Lua itself is not verified here — that needs a real Redis.
 */
public class TestRedisLimiter {

    private static LimitResult evaluate(Object reply, int maxRequests, int windowSeconds) {
        return new RedisLimiterProvider(new StubRedisTemplate(reply))
                .tryAcquire("tenant_a:getItem", maxRequests, windowSeconds);
    }

    @Test
    public void keysAreNamespacedAndTheWindowReachesTheScript() {
        StubRedisTemplate template = new StubRedisTemplate(Arrays.asList(1L, 60L));
        new RedisLimiterProvider(template).tryAcquire("tenant_a:getItem", 5, 60);

        assertEquals(Collections.singletonList("ratelimit:tenant_a:getItem"), template.capturedKeys);
        assertEquals("60", template.capturedArgs[0]);
    }

    @Test
    public void underQuotaIsAllowedAndRemainingCountsDown() {
        LimitResult result = evaluate(Arrays.asList(1L, 60L), 5, 60);

        assertTrue(result.allowed());
        assertEquals(5, result.limit());
        assertEquals(4, result.remaining());
        assertEquals(60, result.resetSeconds());
    }

    /** The quota is inclusive: the fifth of five is still inside the limit. */
    @Test
    public void theLastRequestInsideTheQuotaIsAllowed() {
        LimitResult result = evaluate(Arrays.asList(5L, 42L), 5, 60);

        assertTrue(result.allowed());
        assertEquals(0, result.remaining());
    }

    @Test
    public void overQuotaIsRefusedAndRemainingNeverGoesNegative() {
        LimitResult result = evaluate(Arrays.asList(9L, 42L), 5, 60);

        assertFalse(result.allowed());
        assertEquals(0, result.remaining());
    }

    /** A key with no TTL reports -1; reporting 1 would tell a permanently blocked caller to retry. */
    @Test
    public void aMissingTtlReportsTheConfiguredWindow() {
        assertEquals(60, evaluate(Arrays.asList(3L, -1L), 5, 60).resetSeconds());
    }

    /**
     * Spring Data Redis returns null whenever the connection is queued. Treating that as the first
     * request of a window would disable the limiter while advertising a full quota, so it fails
     * closed instead.
     */
    @Test
    public void anUnusableReplyRefusesRatherThanFailingOpen() {
        for (Object reply : new Object[]{null, Collections.emptyList(), Collections.singletonList("x")}) {
            try {
                evaluate(reply, 5, 60);
                fail("expected refusal for reply: " + reply);
            } catch (ErrorNumException expected) {
                // fails closed as intended
            }
        }
    }
}
