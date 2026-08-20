package org.chenile.limiter.hazelcast.test;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.chenile.limiter.api.LimitResult;
import org.chenile.limiter.hazelcast.HazelcastLimiterProvider;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Runs against a real single-member Hazelcast instance rather than a mocked IMap: the behaviour under
 * test is entirely about how IMap treats entry TTL, which a mock would happily reproduce wrongly.
 */
public class TestHazelcastLimiter {

    private static HazelcastInstance hazelcast;
    private static HazelcastLimiterProvider provider;

    @BeforeClass
    public static void startMember() {
        Config config = new Config();
        config.setClusterName("ratelimit-test-" + System.nanoTime());
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        config.setProperty("hazelcast.logging.type", "none");
        config.setProperty("hazelcast.phone.home.enabled", "false");

        hazelcast = Hazelcast.newHazelcastInstance(config);
        provider = new HazelcastLimiterProvider(hazelcast);
    }

    @AfterClass
    public static void stopMember() {
        if (hazelcast != null) {
            hazelcast.shutdown();
            hazelcast = null;
        }
    }

    @Before
    public void clearCountersBetweenTests() {
        hazelcast.getMap(HazelcastLimiterProvider.MAP_NAME).clear();
    }

    @Test
    public void quotaIsEnforcedWithinTheWindow() {
        assertTrue(provider.tryAcquire("k1", 3, 60).allowed());
        assertTrue(provider.tryAcquire("k1", 3, 60).allowed());

        LimitResult third = provider.tryAcquire("k1", 3, 60);
        assertTrue(third.allowed());
        assertEquals(0, third.remaining());

        assertFalse("the fourth request is over quota", provider.tryAcquire("k1", 3, 60).allowed());
    }

    /** Every update must carry the remaining TTL; put(key, value) resets the entry to never expire. */
    @Test
    public void theWindowActuallyExpiresSoACallerRecovers() throws Exception {
        assertTrue(provider.tryAcquire("k2", 2, 1).allowed());
        assertTrue(provider.tryAcquire("k2", 2, 1).allowed());
        assertFalse(provider.tryAcquire("k2", 2, 1).allowed());

        Thread.sleep(1400);

        assertTrue("the window must expire and hand the caller a fresh quota",
                provider.tryAcquire("k2", 2, 1).allowed());
    }

    /** Re-stamping a full TTL would slide the boundary forward on every request. */
    @Test
    public void continuedTrafficDoesNotPushTheWindowOutForever() throws Exception {
        int windowSeconds = 3;

        provider.tryAcquire("k3", 3, windowSeconds);
        long windowOpenedAt = System.currentTimeMillis();

        while (System.currentTimeMillis() - windowOpenedAt < 1200) {
            provider.tryAcquire("k3", 3, windowSeconds);
            Thread.sleep(150);
        }
        assertFalse("quota should be spent inside the window",
                provider.tryAcquire("k3", 3, windowSeconds).allowed());

        long until = windowOpenedAt + (windowSeconds * 1000L) + 600 - System.currentTimeMillis();
        if (until > 0) {
            Thread.sleep(until);
        }

        assertTrue("the window must close on schedule rather than sliding with traffic",
                provider.tryAcquire("k3", 3, windowSeconds).allowed());
    }

    @Test
    public void resetSecondsReportsTimeRemainingRatherThanTheWholeWindow() throws Exception {
        provider.tryAcquire("k4", 5, 10);
        Thread.sleep(2200);

        LimitResult later = provider.tryAcquire("k4", 5, 10);
        assertTrue("reset should have counted down from 10, got " + later.resetSeconds(),
                later.resetSeconds() < 10);
        assertTrue("and should still be positive", later.resetSeconds() > 0);
    }

    @Test
    public void separateKeysDoNotShareAQuota() {
        assertTrue(provider.tryAcquire("tenant_a", 1, 60).allowed());
        assertFalse(provider.tryAcquire("tenant_a", 1, 60).allowed());

        assertTrue("an unrelated key must be unaffected", provider.tryAcquire("tenant_b", 1, 60).allowed());
    }
}
