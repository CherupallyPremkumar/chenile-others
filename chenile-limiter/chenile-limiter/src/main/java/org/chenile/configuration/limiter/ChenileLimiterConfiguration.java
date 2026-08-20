package org.chenile.configuration.limiter;

import org.chenile.limiter.api.LimiterKeyResolver;
import org.chenile.limiter.api.LimiterProvider;
import org.chenile.limiter.interceptor.LimiterInterceptor;
import org.chenile.limiter.provider.NoOpLimiterProvider;
import org.chenile.limiter.resolver.CompositeKeyResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Wires the limiter. Loads after the provider modules so their beans win over the no-op fallback.
 *
 * <p>Creating {@code limiterInterceptor} does not enforce anything: Chenile builds its chain from an
 * explicit list, so the deployment must name the bean via {@code @InterceptedBy} or
 * {@code chenile.pre.processors}.
 */
@AutoConfiguration(afterName = {
        "org.chenile.configuration.limiter.redis.RedisLimiterAutoConfiguration",
        "org.chenile.configuration.limiter.hazelcast.HazelcastLimiterAutoConfiguration",
        "org.chenile.configuration.limiter.memory.MemoryLimiterAutoConfiguration"})
public class ChenileLimiterConfiguration {

    @Bean
    @ConditionalOnMissingBean(LimiterKeyResolver.class)
    public LimiterKeyResolver limiterKeyResolver() {
        return new CompositeKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean(LimiterProvider.class)
    public LimiterProvider noOpLimiterProvider() {
        return new NoOpLimiterProvider();
    }

    @Bean
    public LimiterInterceptor limiterInterceptor(LimiterProvider limiterProvider,
                                                 LimiterKeyResolver keyResolver) {
        return new LimiterInterceptor(limiterProvider, keyResolver);
    }
}
