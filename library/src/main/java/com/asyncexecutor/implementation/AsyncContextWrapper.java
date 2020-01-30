/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor.implementation;

import android.content.Context;
import android.content.ContextWrapper;

import com.asyncexecutor.Subscription;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * This class extends {@link ContextWrapper} to make it more advanced and suitable for handling
 * results of asynchronous operations.
 */
public class AsyncContextWrapper extends ContextWrapper {
    private final CompositeSubscription<?> subscriptions = new CompositeSubscription<>();
    private final CompositeFuture<?> futures = new CompositeFuture<>();
    private final Executor mainExecutor;

    public AsyncContextWrapper(Context base) {
        super(base);
        mainExecutor = LooperExecutor.newMainThreadExecutor();
    }

    @Override
    public Executor getMainExecutor() {
        return mainExecutor;
    }

    /**
     * It adds new subscription to the context
     *
     * @param subscription {@link Subscription} instance
     */
    public void addSubscription(Subscription subscription) {
        subscriptions.addSubscription(subscription);
    }

    /**
     * It cancels all subscriptions and clears subscriptions list.
     */
    public void cancelSubscriptions() {
        subscriptions.cancel();
    }

    public CompositeSubscription<?> getSubscriptions() {
        return subscriptions;
    }

    /**
     * It adds {@link Future} to the context
     *
     * @param future {@link Future} instance
     * @return same {@link Future} instance
     */
    public Future addFuture(Future future) {
        return futures.addFuture(future);
    }

    /**
     * It cancels all {@link Future} and clears future list.
     *
     * @param mayInterruptIfRunning see the {@link Future#cancel(boolean)}
     * @return {@code true} if this task was cancelled before it completed
     */
    public boolean cancelFutures(boolean mayInterruptIfRunning) {
        boolean result = futures.cancel(mayInterruptIfRunning);
        futures.clearFutures();
        return result;
    }
}
