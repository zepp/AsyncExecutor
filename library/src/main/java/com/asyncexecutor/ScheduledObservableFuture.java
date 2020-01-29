package com.asyncexecutor;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This interface is inherited from {@link ObservableFuture} and {@link ScheduledFuture} to add
 * several new methods and reuse {@link Observable} methods.
 *
 * @param <T> value type
 */
public interface ScheduledObservableFuture<T> extends ObservableFuture<T>, ScheduledFuture<T> {
    /**
     * Getter to return scheduled future type
     *
     * @return {@link ScheduledType} instance
     */
    ScheduledType getType();

    /**
     * This getter returns the scheduled execution time of current future (elapsed time).
     *
     * @param unit {@link TimeUnit} instance
     * @return value in {@link TimeUnit}
     */
    long getScheduledTime(TimeUnit unit);
}
