package org.chenile.limiter.memory;

import org.chenile.limiter.api.LimitResult;

/**
 * Per-key counter for a fixed window: the quota resets in one step once the window elapses.
 *
 * <p>A fixed window admits a burst of up to twice the quota across a boundary. All state is guarded
 * by the instance monitor.
 */
public class FixedWindowCounter {

    private int count;
    private long windowStartMillis = System.currentTimeMillis();
    private long windowMillis;
    private int maxRequests;
    private long lastAccessMillis = System.currentTimeMillis();

    public synchronized LimitResult evaluate(int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowSizeMillis = windowSeconds * 1000L;

        this.windowMillis = windowSizeMillis;
        this.maxRequests = maxRequests;
        this.lastAccessMillis = now;

        long elapsedMillis = now - windowStartMillis;
        if (elapsedMillis >= windowSizeMillis) {
            windowStartMillis = now;
            count = 0;
            elapsedMillis = 0;
        }

        // Rounded up so a client honouring the reset never retries a fraction of a second early.
        long resetSeconds = Math.max(1, (windowSizeMillis - elapsedMillis + 999) / 1000);
        count++;

        return new LimitResult(count <= maxRequests, maxRequests,
                Math.max(0, maxRequests - count), resetSeconds);
    }

    synchronized boolean isExpired(long now) {
        return windowMillis > 0 && (now - windowStartMillis) >= windowMillis;
    }

    synchronized boolean isOverQuota(long now) {
        return !isExpired(now) && maxRequests > 0 && count >= maxRequests;
    }

    synchronized long lastAccessMillis() {
        return lastAccessMillis;
    }
}
