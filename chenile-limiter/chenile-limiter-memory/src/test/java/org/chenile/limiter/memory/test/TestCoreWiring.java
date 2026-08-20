package org.chenile.limiter.memory.test;

import org.chenile.configuration.limiter.ChenileLimiterConfiguration;
import org.chenile.configuration.limiter.memory.MemoryLimiterAutoConfiguration;
import org.chenile.limiter.api.LimiterKeyResolver;
import org.chenile.limiter.api.LimiterProvider;
import org.chenile.limiter.interceptor.LimiterInterceptor;
import org.chenile.limiter.memory.InMemoryLimiterProvider;
import org.chenile.limiter.provider.NoOpLimiterProvider;
import org.chenile.limiter.resolver.CompositeKeyResolver;
import org.chenile.limiter.resolver.TenantKeyResolver;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class TestCoreWiring {

    private final ApplicationContextRunner coreOnly = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChenileLimiterConfiguration.class));

    private final ApplicationContextRunner coreAndProvider = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChenileLimiterConfiguration.class,
                    MemoryLimiterAutoConfiguration.class));

    /**
     * The interceptor bean must exist even with no real provider: chenile.pre.processors resolves
     * bean names lazily on the first request, so omitting it would turn a missing provider into a
     * per-request failure.
     */
    @Test
    public void coreWithoutAProviderStillPublishesTheInterceptor() {
        coreOnly.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LimiterInterceptor.class);
            assertThat(context).getBean(LimiterProvider.class).isInstanceOf(NoOpLimiterProvider.class);
        });
    }

    @Test
    public void addingAProviderReplacesTheNoOp() {
        coreAndProvider.run(context -> {
            assertThat(context).hasSingleBean(LimiterInterceptor.class);
            assertThat(context).getBean(LimiterProvider.class)
                    .isInstanceOf(InMemoryLimiterProvider.class);
            assertThat(context).doesNotHaveBean(NoOpLimiterProvider.class);
        });
    }

    @Test
    public void theDefaultKeyResolverIsPerUserWithinTenant() {
        coreAndProvider.run(context -> assertThat(context).getBean(LimiterKeyResolver.class)
                .isInstanceOf(CompositeKeyResolver.class));
    }

    @Test
    public void anApplicationCanSubstituteItsOwnKeyResolver() {
        coreAndProvider.withBean(LimiterKeyResolver.class, TenantKeyResolver::new)
                .run(context -> assertThat(context).getBean(LimiterKeyResolver.class)
                        .isInstanceOf(TenantKeyResolver.class));
    }

    @Test
    public void anApplicationCanSubstituteItsOwnProvider() {
        coreAndProvider.withBean(LimiterProvider.class, () -> (key, max, window) -> null)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(InMemoryLimiterProvider.class);
                    assertThat(context).doesNotHaveBean(NoOpLimiterProvider.class);
                });
    }
}
