package com.asyncexecutor.implementation;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RunnableFuture;

/**
 * This class extends {@link CompletableFuture<T>} to implement {@link RunnableFuture<T>} interface.
 * It is required by {@link java.util.concurrent.AbstractExecutorService} internals.
 *
 * @param <T> type
 */
class AsyncTask<T> extends CompletableFuture<T> implements RunnableFuture<T> {
    AsyncTask(Executor executor, Callable<T> callable) {
        super(executor, callable);
    }

    AsyncTask(Executor executor, Runnable runnable, T result) {
        super(executor, runnable, result);
    }
}
