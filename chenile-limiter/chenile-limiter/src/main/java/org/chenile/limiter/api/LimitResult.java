package org.chenile.limiter.api;

/** Outcome of one quota check. */
public record LimitResult(
    boolean allowed,
    int limit,
    int remaining,
    long resetSeconds
) {}
