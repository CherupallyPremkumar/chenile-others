package org.chenile.configuration.limiter.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import org.chenile.limiter.api.LimiterProvider;
import org.chenile.limiter.hazelcast.HazelcastLimiterProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers the Hazelcast provider ahead of the in-memory fallback.
 *
 * <p>{@link ConditionalOnBean} matters as much as {@link ConditionalOnClass}: the hazelcast jar
 * arrives transitively from other Chenile modules, and Spring Boot only creates a
 * {@link HazelcastInstance} when a config resource or {@code Config} bean is present. Requiring one
 * that was never created would fail the context at startup.
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 200)
@ConditionalOnClass(HazelcastInstance.class)
@ConditionalOnBean(HazelcastInstance.class)
@ConditionalOnProperty(prefix = "chenile.limiter", name = "provider", havingValue = "hazelcast",
        matchIfMissing = true)
public class HazelcastLimiterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LimiterProvider.class)
    public LimiterProvider hazelcastLimiterProvider(HazelcastInstance hazelcastInstance) {
        return new HazelcastLimiterProvider(hazelcastInstance);
    }
}
