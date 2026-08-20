package org.chenile.limiter.redis.test;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Answers script calls from a canned reply and records what it was asked. {@code afterPropertiesSet}
 * is overridden because the real one insists on a connection factory.
 */
class StubRedisTemplate extends StringRedisTemplate {

    private final Object reply;

    List<String> capturedKeys;
    Object[] capturedArgs;

    StubRedisTemplate() {
        this(null);
    }

    StubRedisTemplate(Object reply) {
        this.reply = reply;
    }

    @Override
    public void afterPropertiesSet() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        this.capturedKeys = keys;
        this.capturedArgs = args;
        return (T) reply;
    }
}
