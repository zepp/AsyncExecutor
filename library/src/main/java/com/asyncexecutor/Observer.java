package com.asyncexecutor;

/**
 * This interface describes a type to handle results of some sort of computation that may be
 * performed asynchronously.
 *
 * @param <T> data type to be handled
 */
@FunctionalInterface
public interface Observer<T> {
    /**
     * This method handles values that are emitted by {@link Observable<T>}
     *
     * @param result value or <code>null</code>.
     * @param e      exception that is not null in case of operation completed with error
     */
    void accept(T result, Exception e);

    /**
     * This method is called when observer has been subscribed to {@link Observable<T>}
     *
     * @param subscription to manage subscription status
     */
    default void onSubscribed(Subscription<T> subscription) {
    }

    /**
     * This method is called when observer has been unsubscribed from {@link Observable<T>}
     *
     * @param subscription to manage subscription status
     */
    default void onUnsubscribed(Subscription<T> subscription) {
    }
}
