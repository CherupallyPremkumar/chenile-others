package org.chenile.limiter.memory.test;

import org.chenile.limiter.memory.InMemoryLimiterProvider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The table must stay bounded even when the key is influenced by the caller. */
public class TestInMemoryProviderBounds {

    @Test
    public void theTableNeverGrowsBeyondTheCap() {
        InMemoryLimiterProvider provider = new InMemoryLimiterProvider(100);

        for (int i = 0; i < 1000; i++) {
            provider.tryAcquire("key-" + i, 5, 60);
        }

        assertTrue("tracked " + provider.trackedKeyCount() + " keys, cap was 100",
                provider.trackedKeyCount() <= 100);
    }

    @Test
    public void closedWindowsAreReclaimed() throws Exception {
        InMemoryLimiterProvider provider = new InMemoryLimiterProvider();
        provider.tryAcquire("short", 5, 1);
        assertEquals(1, provider.trackedKeyCount());

        Thread.sleep(1100);
        provider.purgeExpired();

        assertEquals(0, provider.trackedKeyCount());
    }

    /** Evicting a refused key would hand an already-blocked caller a fresh allowance. */
    @Test
    public void aRefusedKeySurvivesEvictionWhileOthersAreAvailable() {
        InMemoryLimiterProvider provider = new InMemoryLimiterProvider(10);

        provider.tryAcquire("blocked", 1, 600);
        assertFalse(provider.tryAcquire("blocked", 1, 600).allowed());

        for (int i = 0; i < 200; i++) {
            provider.tryAcquire("filler-" + i, 100, 600);
        }

        assertFalse("the blocked key must not have been reset by eviction",
                provider.tryAcquire("blocked", 1, 600).allowed());
    }

    @Test
    public void aNonPositiveCapIsRejected() {
        try {
            new InMemoryLimiterProvider(0);
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // rejected as intended
        }
    }
}
