package com.asyncexecutor.implementation;

import com.asyncexecutor.ObservableFuture;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * This class extends {@link AbstractFuture<T>} class to implement code that perform computation
 * or evaluation of values to be emitted. This class encapsulate {@link Callable<T>} or {@link Runnable}
 * that performs actual job.
 *
 * @param <T>
 */
public class CompletableFuture<T> extends AbstractFuture<T> implements Runnable {
    protected final Callable<T> callable;
    protected final Runnable runnable;
    private volatile Thread thread;

    public CompletableFuture(Executor executor, boolean isMultiCompletable, Callable<T> callable) {
        super(executor, isMultiCompletable);
        this.callable = callable;
        this.runnable = null;
    }

    public CompletableFuture(Executor executor, Callable<T> callable) {
        this(executor, false, callable);
    }

    public CompletableFuture(Executor executor, boolean isMultiCompletable, Runnable runnable, T result) {
        super(executor, isMultiCompletable);
        this.runnable = runnable;
        this.callable = null;
        this.result = result;
    }

    public CompletableFuture(Executor executor, Runnable runnable, T result) {
        this(executor, false, runnable, result);
    }

    public CompletableFuture(Executor executor, Runnable runnable) {
        this(executor, false, runnable, null);
    }

    /**
     * This method runs one of supplied functional interfaces to evaluate value to be emitted.
     */
    @Override
    public void run() {
        thread = Thread.currentThread();
        if (setState(State.COMPLETING)) {
            try {
                if (runnable == null) {
                    emit(callable.call());
                } else {
                    runnable.run();
                    emit(result);
                }
            } catch (Exception e) {
                emit(e);
            }
        }
    }

    /**
     * Submits current future to {@link Executor} to starts background evaluation. If future is
     * already completed then it's state is change to {@link com.asyncexecutor.implementation.AbstractFuture.State#INITIAL}
     * at first.
     *
     * @return current future
     */
    @Override
    public synchronized ObservableFuture<T> submit() {
        if (getState() == State.INITIAL) {
            getExecutor().execute(this);
        } else {
            getExecutor().execute(() -> {
                if (setState(State.INITIAL)) {
                    run();
                }
            });
        }
        return this;
    }

    /**
     * This method tries to interrupt current future evaluation
     *
     * @param mayInterruptIfRunning if it is true then {@link Thread} is processing current
     *                              future is interrupted by {@link Thread#interrupt}.
     * @return true if future has been successfully canceled or false otherwise
     */
    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            if (mayInterruptIfRunning && thread != null) {
                thread.interrupt();
            }
        }
        return false;
    }
}
