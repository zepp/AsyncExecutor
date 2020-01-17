package com.asyncexecutor;

import java.util.concurrent.Executor;

/**
 * This interface describes a type that provides a way to subscribe to stream of values or
 * results of some background computation. This stream may contain successful results or errors.
 *
 * @param <T> values type
 */
public interface Observable<T> {
    /**
     * This method subscribes to current {@link Observable<T>}. It handles only successful results.
     *
     * @param consumer lambda or object to handle results
     * @return {@link Subscription<T>} to manage the subscription status
     */
    Subscription<T> accept(Consumer<T> consumer);

    /**
     * This method subscribes to current {@link Observable<T>}. It handles only successful results.
     * Handler is executed on specified <code>executor</code>.
     *
     * @param executor executor to be used to run {@link Consumer<T>::accept}
     * @param consumer lambda or object to handle results
     * @return {@link Subscription<T>} to manage the subscription status
     */
    Subscription<T> accept(Executor executor, Consumer<T> consumer);

    /**
     * This method subscribes to current {@link Observable<T>}. Successful results and errors can
     * be handled since {@link Observer<T>} is more advanced then {@link Consumer<T>} and provides
     * an api to handle errors and track moments when subscription is registered or unregistered.
     *
     * @param observer lambda or object to handle results
     * @return {@link Subscription<T>} to manage the subscription status
     */
    Subscription<T> observe(Observer<T> observer);

    /**
     * This method subscribes to current {@link Observable<T>}. Successful results and errors can
     * be handled since {@link Observer<T>} is more advanced then {@link Consumer<T>} and provides
     * an api to handle errors and track moments when subscription is registered or unregistered.
     * Handler is executed on specified <code>executor</code>.
     *
     * @param executor executor to be used to run {@link Observer<T>::accept}
     * @param observer lambda or object to handle results
     * @return {@link Subscription<T>} to manage the subscription status
     */
    Subscription<T> observe(Executor executor, Observer<T> observer);
}
