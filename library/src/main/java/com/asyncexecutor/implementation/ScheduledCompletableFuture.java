package com.asyncexecutor.implementation;

import androidx.annotation.CallSuper;

import com.asyncexecutor.ScheduledObservableFuture;
import com.asyncexecutor.ScheduledType;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * This class extends {@link CompletableFuture} and implements {@link ScheduledObservableFuture}
 *
 * @param <T> emitted value type
 */
public class ScheduledCompletableFuture<T> extends CompletableFuture<T> implements ScheduledObservableFuture<T> {
    private final ScheduledType type;
    private final long periodNanos;
    private volatile long scheduledNanos;

    public ScheduledCompletableFuture(Executor executor, Callable<T> callable, long scheduledNanos) {
        super(executor, callable);
        this.type = ScheduledType.SINGLE_SHOT;
        this.periodNanos = 0;
        this.scheduledNanos = System.nanoTime() + scheduledNanos;
    }

    public ScheduledCompletableFuture(Executor executor, Runnable runnable, long scheduledNanos) {
        super(executor, runnable);
        this.type = ScheduledType.SINGLE_SHOT;
        this.periodNanos = 0;
        this.scheduledNanos = System.nanoTime() + scheduledNanos;
    }

    public ScheduledCompletableFuture(Executor executor, Callable<T> callable, ScheduledType type, long initialDelay, long period, TimeUnit unit) {
        super(executor, true, callable);
        this.type = type;
        this.periodNanos = unit.toNanos(period);
        this.scheduledNanos = System.nanoTime() + unit.toNanos(initialDelay);
    }

    public ScheduledCompletableFuture(Executor executor, Runnable runnable, ScheduledType type, long initialDelay, long period, TimeUnit unit) {
        super(executor, true, runnable, null);
        this.type = type;
        this.periodNanos = unit.toNanos(period);
        this.scheduledNanos = System.nanoTime() + unit.toNanos(initialDelay);
    }

    @Override
    public ScheduledType getType() {
        return type;
    }

    @Override
    public long getScheduledTime(TimeUnit unit) {
        return unit.convert(scheduledNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * This method re-calculate next scheduled execution time.
     */
    @CallSuper
    protected void reschedule() {
        if (type == ScheduledType.FIXED_RATE) {
            scheduledNanos += periodNanos;
            setState(State.INITIAL);
        } else if (type == ScheduledType.FIXED_DELAY) {
            scheduledNanos = System.nanoTime() + periodNanos;
            setState(State.INITIAL);
        }
    }

    @Override
    protected synchronized boolean emit(T result) {
        if (super.emit(result)) {
            reschedule();
            return true;
        }
        return false;
    }

    @Override
    protected synchronized boolean emit(Exception e) {
        if (super.emit(e)) {
            reschedule();
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            reschedule();
            return true;
        }
        return false;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(scheduledNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(getDelay(TimeUnit.NANOSECONDS), o.getDelay(TimeUnit.NANOSECONDS));
    }
}
