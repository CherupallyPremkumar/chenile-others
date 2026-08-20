package org.chenile.limiter.redis.test;

import org.chenile.configuration.limiter.memory.MemoryLimiterAutoConfiguration;
import org.chenile.configuration.limiter.redis.RedisLimiterAutoConfiguration;
import org.chenile.limiter.api.LimiterProvider;
import org.chenile.limiter.memory.InMemoryLimiterProvider;
import org.chenile.limiter.redis.RedisLimiterProvider;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which provider wins when more than one is on the classpath.
 *
 * <p>Both modules present is the ordinary setup. Every provider claims the same bean type under
 * {@code @ConditionalOnMissingBean}, so the winner is decided by auto-configuration order, and
 * Spring Boot's default is to sort by class name — which would put the per-process limiter ahead of
 * Redis. This test is what stops that regressing.
 */
public class TestProviderPrecedence {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(StringRedisTemplate.class, StubRedisTemplate::new)
            .withConfiguration(AutoConfigurations.of(
                    MemoryLimiterAutoConfiguration.class,
                    RedisLimiterAutoConfiguration.class));

    @Test
    public void redisWinsOverTheInMemoryFallbackWhenBothArePresent() {
        runner.run(context -> assertThat(context).getBean(LimiterProvider.class)
                .isInstanceOf(RedisLimiterProvider.class));
    }

    @Test
    public void exactlyOneProviderIsRegistered() {
        runner.run(context -> assertThat(context).getBeans(LimiterProvider.class).hasSize(1));
    }

    @Test
    public void theProviderPropertyOverridesTheDefaultOrdering() {
        runner.withPropertyValues("chenile.limiter.provider=memory")
                .run(context -> assertThat(context).getBean(LimiterProvider.class)
                        .isInstanceOf(InMemoryLimiterProvider.class));
    }

    @Test
    public void namingRedisExplicitlyKeepsTheInMemoryFallbackOut() {
        runner.withPropertyValues("chenile.limiter.provider=redis")
                .run(context -> assertThat(context).getBean(LimiterProvider.class)
                        .isInstanceOf(RedisLimiterProvider.class));
    }

    /**
     * The Redis jar on the classpath does not imply a usable template. Without a
     * {@code StringRedisTemplate} bean the configuration must stand down rather than fail the
     * context on an unsatisfied dependency.
     */
    @Test
    public void redisStandsDownWhenNoTemplateBeanExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MemoryLimiterAutoConfiguration.class,
                        RedisLimiterAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean(LimiterProvider.class)
                            .isInstanceOf(InMemoryLimiterProvider.class);
                });
    }
}
