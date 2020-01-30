/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This interface is inherited from {@link AsyncExecutorService} and {@link ScheduledExecutorService}
 * to enrich api and add new methods. Most of the methods are overridden to return {@link ScheduledObservableFuture}
 * but also several new methods are added.
 */
public interface ScheduledAsyncExecutorService extends AsyncExecutorService, ScheduledExecutorService {
    @Override
    ScheduledObservableFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    @Override
    <V> ScheduledObservableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    @Override
    ScheduledObservableFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    @Override
    ScheduledObservableFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

    <V> ScheduledObservableFuture<V> scheduleAtFixedRate(Callable<V> callable, long initialDelay, long period, TimeUnit unit);

    <V> ScheduledObservableFuture<V> scheduleWithFixedDelay(Callable<V> callable, long initialDelay, long delay, TimeUnit unit);
}
