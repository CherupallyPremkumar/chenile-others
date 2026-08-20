package org.chenile.limiter.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.chenile.core.annotation.ChenileAnnotation;

/**
 * Declares a request quota on a Chenile controller class or controller method.
 *
 * <p>Only collected from {@code @ChenileController} beans and their mapped methods. On a service
 * interface or implementation it is silently ignored.
 */
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ChenileAnnotation
public @interface ChenileLimiter {

    /** Maximum requests allowed within the window. */
    int maxRequests() default 100;

    /** Window length in seconds. */
    int windowSeconds() default 60;
}
