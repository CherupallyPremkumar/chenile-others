package org.chenile.limiter.redis;

import org.chenile.base.exception.ErrorNumException;
import org.chenile.limiter.api.LimitResult;
import org.chenile.limiter.api.LimiterProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

/**
 * Distributed provider backed by Redis, enforcing one quota across every replica. The whole
 * read-modify-write runs as a single Lua script so replicas cannot interleave.
 */
public class RedisLimiterProvider implements LimiterProvider {

    static final String KEY_PREFIX = "ratelimit:";

    // The expiry is re-applied whenever the TTL is missing, not only on the first increment: a key
    // with no TTL would otherwise stay above the quota permanently.
    private static final String LUA_SCRIPT =
        "local current = tonumber(redis.call('INCR', KEYS[1])); " +
        "local ttl = tonumber(redis.call('TTL', KEYS[1])); " +
        "if current == 1 or ttl == nil or ttl < 0 then " +
        "    redis.call('EXPIRE', KEYS[1], ARGV[1]); " +
        "    ttl = tonumber(ARGV[1]); " +
        "end " +
        "return {current, ttl};";

    private final StringRedisTemplate redisTemplate;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> script;

    public RedisLimiterProvider(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    @Override
    public LimitResult tryAcquire(String key, int maxRequests, int windowSeconds) {
        List<?> reply = redisTemplate.execute(script, Collections.singletonList(KEY_PREFIX + key),
                String.valueOf(windowSeconds));

        long currentCount = requireNumber(reply, 0);
        long ttlSeconds = reply.size() > 1 && reply.get(1) instanceof Number n ? n.longValue() : windowSeconds;
        if (ttlSeconds <= 0) {
            ttlSeconds = windowSeconds;
        }

        return new LimitResult(currentCount <= maxRequests, maxRequests,
                (int) Math.max(0, maxRequests - currentCount), Math.max(1, ttlSeconds));
    }

    /**
     * Fails closed. Spring Data Redis returns null when the connection is queued (pipeline,
     * transaction support, SessionCallback); treating that as "first request of the window" would
     * disable the limiter while advertising a full quota.
     */
    private static long requireNumber(List<?> reply, int index) {
        if (reply == null || reply.size() <= index || !(reply.get(index) instanceof Number number)) {
            throw new ErrorNumException(500, "RATE_LIMIT_BACKEND_UNAVAILABLE",
                    "Redis rate limit script returned an unusable reply: " + reply
                            + ". The quota cannot be verified, so the request is refused.");
        }
        return number.longValue();
    }
}
