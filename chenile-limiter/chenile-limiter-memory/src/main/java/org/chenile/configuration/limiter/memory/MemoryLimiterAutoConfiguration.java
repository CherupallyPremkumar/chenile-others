package org.chenile.configuration.limiter.memory;

import org.chenile.limiter.api.LimiterProvider;
import org.chenile.limiter.memory.InMemoryLimiterProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers the single-node provider as the last resort.
 *
 * <p>The ordering is load-bearing. All providers claim the same bean type under
 * {@link ConditionalOnMissingBean}, so whichever is processed first wins, and Spring Boot's default
 * is to sort by class name. This must sort after every distributed provider, never before, or a
 * deployment carrying both silently gets a per-process limiter.
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "chenile.limiter", name = "provider", havingValue = "memory",
        matchIfMissing = true)
public class MemoryLimiterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LimiterProvider.class)
    public LimiterProvider inMemoryLimiterProvider() {
        return new InMemoryLimiterProvider();
    }
}
