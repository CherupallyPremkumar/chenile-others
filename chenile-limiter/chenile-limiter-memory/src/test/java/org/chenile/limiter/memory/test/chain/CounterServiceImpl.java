package org.chenile.limiter.memory.test.chain;

import java.util.concurrent.atomic.AtomicInteger;

public class CounterServiceImpl implements CounterService {

    public static final AtomicInteger INVOCATIONS = new AtomicInteger();

    @Override
    public String hit() {
        INVOCATIONS.incrementAndGet();
        return "ok";
    }
}
