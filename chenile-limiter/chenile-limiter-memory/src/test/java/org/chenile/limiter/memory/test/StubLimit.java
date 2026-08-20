package org.chenile.limiter.memory.test;

import org.chenile.limiter.annotation.ChenileLimiter;

import java.lang.annotation.Annotation;

/** Stands in for the annotation instance Chenile would have collected onto an OperationDefinition. */
public record StubLimit(int maxRequests, int windowSeconds) implements ChenileLimiter {

    @Override
    public Class<? extends Annotation> annotationType() {
        return ChenileLimiter.class;
    }
}
