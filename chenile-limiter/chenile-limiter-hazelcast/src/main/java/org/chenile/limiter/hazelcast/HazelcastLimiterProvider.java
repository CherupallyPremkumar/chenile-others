package org.chenile.limiter.hazelcast;

import com.hazelcast.core.EntryView;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.chenile.limiter.api.LimitResult;
import org.chenile.limiter.api.LimiterProvider;

import java.util.concurrent.TimeUnit;

/**
 * Cluster-wide provider backed by a Hazelcast {@link IMap}.
 *
 * <p>Each update carries the time remaining from when the window opened. {@code IMap.put(key, value)}
 * would reset the entry to the map default of "never expire", and re-stamping a full TTL would slide
 * the boundary forward on every request.
 */
public class HazelcastLimiterProvider implements LimiterProvider {

    /** Exposed so a deployment can configure backup count, eviction and so on. */
    public static final String MAP_NAME = "chenile:ratelimit";

    private final HazelcastInstance hazelcastInstance;

    public HazelcastLimiterProvider(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public LimitResult tryAcquire(String key, int maxRequests, int windowSeconds) {
        IMap<String, Integer> map = hazelcastInstance.getMap(MAP_NAME);

        map.lock(key);
        try {
            EntryView<String, Integer> entry = map.getEntryView(key);
            long remainingMillis = remainingMillis(entry, System.currentTimeMillis());

            if (entry == null || remainingMillis <= 0) {
                return openWindow(map, key, maxRequests, windowSeconds);
            }

            int count = entry.getValue() + 1;
            map.put(key, count, remainingMillis, TimeUnit.MILLISECONDS);

            return new LimitResult(count <= maxRequests, maxRequests,
                    Math.max(0, maxRequests - count), toResetSeconds(remainingMillis));
        } finally {
            map.unlock(key);
        }
    }

    private LimitResult openWindow(IMap<String, Integer> map, String key, int maxRequests, int windowSeconds) {
        map.put(key, 1, windowSeconds, TimeUnit.SECONDS);
        return new LimitResult(maxRequests >= 1, maxRequests, Math.max(0, maxRequests - 1), windowSeconds);
    }

    /**
     * @return milliseconds left in the current window, or 0 if there is none. An entry with no expiry
     *         is treated as expired so a stranded counter heals on first contact.
     */
    private static long remainingMillis(EntryView<String, Integer> entry, long now) {
        if (entry == null) {
            return 0;
        }
        long expiresAt = entry.getExpirationTime();
        if (expiresAt <= 0 || expiresAt == Long.MAX_VALUE) {
            return 0;
        }
        return expiresAt - now;
    }

    private static long toResetSeconds(long remainingMillis) {
        return Math.max(1, (remainingMillis + 999) / 1000);
    }
}
