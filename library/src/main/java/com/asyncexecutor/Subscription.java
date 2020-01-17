package com.asyncexecutor;

/**
 * This interface describes a type to manage a subscription to {@link Observable<T>}.
 *
 * @param <T> type of values produced by {@link Observable}
 */
public interface Subscription<T> extends Comparable<Subscription<T>> {
    /**
     * This method returns subscription ID. It is provided to be able to store item in
     * {@link java.util.Map} or {@link java.util.Set}.
     */
    int getId();

    /**
     * It returns {@link Observable<T>} that produces stream of values.
     *
     * @return {@link Observable<T>} instance
     */
    Observable<T> getObservable();

    /**
     * This method is used to cancel subscription. It detaches {@link Consumer<T>} or {@link Observable<T>}
     * from {@link Observable<T>}. No more values are delivered after this method call. Also it
     * releases an object reference to make it available for garbage collection.
     */
    void cancel();
}
