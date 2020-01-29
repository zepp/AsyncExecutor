package com.asyncexecutor;

/**
 * This enum describes type of {@link ScheduledObservableFuture}
 */
public enum ScheduledType {
    /**
     * Future that goes off a once
     */
    SINGLE_SHOT,
    /**
     * Future that goes off multiple times at fixed rate. Next scheduled time is
     * {@code initial delay + period * n}
     */
    FIXED_RATE,
    /**
     * Future that goes off multiple times with fixed rate. Next scheduled time is
     * {@code completion time + period}
     */
    FIXED_DELAY
}
