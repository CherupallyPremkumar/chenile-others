package org.chenile.configuration.limiter.redis;

import org.chenile.limiter.api.LimiterProvider;
import org.chenile.limiter.redis.RedisLimiterProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Registers the Redis provider ahead of the in-memory fallback, so a deployment carrying both gets
 * the one that holds across replicas.
 *
 * <p>{@link ConditionalOnBean} matters as much as {@link ConditionalOnClass}: the jar being present
 * does not mean a usable template exists, and requiring one that was never created would fail the
 * context at startup.
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "chenile.limiter", name = "provider", havingValue = "redis",
        matchIfMissing = true)
public class RedisLimiterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LimiterProvider.class)
    public LimiterProvider redisLimiterProvider(StringRedisTemplate redisTemplate) {
        return new RedisLimiterProvider(redisTemplate);
    }
}
