package org.chenile.limiter.memory;

import org.chenile.limiter.api.LimitResult;
import org.chenile.limiter.api.LimiterProvider;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Single-node provider. Enforces a quota per process, so N replicas allow N times the configured
 * rate; use the Redis or Hazelcast provider when the limit must hold across a cluster.
 *
 * <p>The table is capped and closed windows are reclaimed, so a caller-influenced key cannot grow it
 * without bound.
 */
public class InMemoryLimiterProvider implements LimiterProvider {

    public static final int DEFAULT_MAX_TRACKED_KEYS = 100_000;

    private static final int EVICTION_BATCH_DIVISOR = 10;

    private final Map<String, FixedWindowCounter> counters = new ConcurrentHashMap<>();
    private final int maxTrackedKeys;

    public InMemoryLimiterProvider() {
        this(DEFAULT_MAX_TRACKED_KEYS);
    }

    public InMemoryLimiterProvider(int maxTrackedKeys) {
        if (maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("maxTrackedKeys must be positive, got " + maxTrackedKeys);
        }
        this.maxTrackedKeys = maxTrackedKeys;
    }

    @Override
    public LimitResult tryAcquire(String key, int maxRequests, int windowSeconds) {
        FixedWindowCounter counter = counters.get(key);
        if (counter == null) {
            if (counters.size() >= maxTrackedKeys) {
                makeRoom(System.currentTimeMillis());
            }
            counter = counters.computeIfAbsent(key, k -> new FixedWindowCounter());
        }
        return counter.evaluate(maxRequests, windowSeconds);
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, FixedWindowCounter> entry : counters.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                counters.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public int trackedKeyCount() {
        return counters.size();
    }

    /**
     * Closed windows go first. Keys currently being refused are evicted last, since dropping one
     * hands an already-blocked caller a fresh allowance.
     */
    private void makeRoom(long now) {
        purgeExpired();
        if (counters.size() < maxTrackedKeys) {
            return;
        }
        int batch = Math.max(1, maxTrackedKeys / EVICTION_BATCH_DIVISOR);
        if (evictOldest(batch, entry -> !entry.getValue().isOverQuota(now)) > 0) {
            return;
        }
        evictOldest(batch, entry -> true);
    }

    private int evictOldest(int batch, Predicate<Map.Entry<String, FixedWindowCounter>> eligible) {
        List<Map.Entry<String, FixedWindowCounter>> victims = counters.entrySet().stream()
                .filter(eligible)
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccessMillis()))
                .limit(batch)
                .toList();

        int removed = 0;
        for (Map.Entry<String, FixedWindowCounter> victim : victims) {
            if (counters.remove(victim.getKey(), victim.getValue())) {
                removed++;
            }
        }
        return removed;
    }
}
