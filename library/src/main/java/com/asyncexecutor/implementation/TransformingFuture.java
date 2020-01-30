/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor.implementation;

import com.asyncexecutor.ObservableFuture;
import com.asyncexecutor.Observer;
import com.asyncexecutor.Transformer;

import java.util.concurrent.Executor;

/**
 * This class implements a phase that processes values emitted by another {@link AbstractFuture}.
 *
 * @param <D> type of values to be re-emitted
 * @param <T> type of values emitted by parent
 */
class TransformingFuture<D, T> extends AbstractFuture<D> implements Observer<T> {
    private final AbstractFuture<T> parent;
    private final Transformer<T, D> transformer;

    TransformingFuture(AbstractFuture<T> parent, Executor executor, boolean isMultiCompletable, Transformer<T, D> transformer) {
        super(executor, isMultiCompletable);
        this.parent = parent;
        this.transformer = transformer;
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            return parent.cancel(mayInterruptIfRunning);
        }
        return false;
    }

    @Override
    public ObservableFuture<D> submit() {
        parent.submit();
        return this;
    }

    /**
     * This method handle values and errors that are emitted by parent {@link AbstractFuture}
     *
     * @param t value to be processed
     * @param e exception that is not null in case of operation completed with error
     */
    @Override
    public void accept(T t, Exception e) {
        try {
            emit(transformer.transform(t, e));
        } catch (Exception exception) {
            emit(exception);
        }
    }
}
