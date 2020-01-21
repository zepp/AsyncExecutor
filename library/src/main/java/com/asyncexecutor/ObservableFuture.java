package com.asyncexecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * This interface combines {@link Future<T>}, {@link Observable<T>} and {@link Completable<T>}.
 *
 * @param <T> value type
 */
public interface ObservableFuture<T> extends Future<T>, Observable<T>, Completable<T> {
    /**
     * This method provides a way to transform values returned by current {@link ObservableFuture<T>}
     *
     * @param executor    executor to run transformer
     * @param transformer transformer that process values
     * @param <D>         type of returned values
     * @return new {@link ObservableFuture<T>}
     */
    <D> ObservableFuture<D> map(Executor executor, Transformer<T, D> transformer);

    /**
     * This method provides a way to transform values returned by current {@link ObservableFuture<T>}
     *
     * @param transformer transformer that process values
     * @param <D>         type of returned values
     * @return new {@link ObservableFuture<T>}
     */
    <D> ObservableFuture<D> map(Transformer<T, D> transformer);

    /**
     * This method initiates evaluation of current {@link ObservableFuture<T>}
     *
     * @return
     */
    ObservableFuture<T> submit();

    /**
     * This method is wrapper around {@link Future#get} that packs checked exception to {@link RuntimeException}
     *
     * @return current result
     */
    T getResult();
}
