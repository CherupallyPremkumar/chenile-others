package org.chenile.limiter.memory.test.chain;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@SpringBootApplication(scanBasePackages = {"org.chenile"})
@PropertySource("classpath:org/chenile/limiter/memory/test/chain/TestChain.properties")
public class ChainSpringConfig {

    /** Bean name matches the "name" field of the service JSON. */
    @Bean("_counterService_")
    public CounterService counterServiceImpl() {
        return new CounterServiceImpl();
    }
}
