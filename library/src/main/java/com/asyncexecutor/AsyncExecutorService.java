package com.asyncexecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * This interface extends standard {@link ExecutorService} to make it more advanced.
 * {@link AsyncExecutorService#submit} method returns {@link ObservableFuture} that implements
 * rich api to handle result.
 */
public interface AsyncExecutorService extends ExecutorService {
    /**
     * This method creates {@link com.asyncexecutor.implementation.CompletableFuture} from provided
     * {@link Callable} and submits it for evaluation.
     *
     * @param task {@link Callable<T>} instance
     * @param <T>  value type
     * @return future reference
     */
    @Override
    <T> ObservableFuture<T> submit(Callable<T> task);

    /**
     * This method creates {@link com.asyncexecutor.implementation.CompletableFuture} from provided
     * {@link Runnable} and submits it for evaluation.
     *
     * @param task   {@link Runnable} instance
     * @param result value to be considered as result of evaluation
     * @param <T>    value type
     * @return future reference
     */
    @Override
    <T> ObservableFuture<T> submit(Runnable task, T result);

    /**
     * This method creates {@link com.asyncexecutor.implementation.CompletableFuture} from provided
     * {@link Runnable} and submits it for evaluation.
     *
     * @param task {@link Runnable} instance
     * @return future reference
     */
    @Override
    ObservableFuture<?> submit(Runnable task);

    /**
     * This method block execution of current {@link Thread} until all submitted tasks are processed.
     *
     * @return true if all tasks are processed, false otherwise
     * @throws InterruptedException
     */
    boolean awaitCompletion() throws InterruptedException;
}
