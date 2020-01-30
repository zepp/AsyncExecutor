/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor.implementation;

import androidx.annotation.CallSuper;

import com.asyncexecutor.Consumer;
import com.asyncexecutor.ObservableFuture;
import com.asyncexecutor.Observer;
import com.asyncexecutor.Subscription;
import com.asyncexecutor.Transformer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base class that implements {@link ObservableFuture<T>} interface and extends {@link AbstractObservable<T>}.
 * It emits single value or stream of values depending on future type. Methods like {@link AbstractFuture#get()}
 * return last emitted value.
 *
 * @param <T> type of value to be emitted
 */
public abstract class AbstractFuture<T> extends AbstractObservable<T> implements ObservableFuture<T> {
    private final static int NANOS_TO_MILLIS = 1000000;
    private final boolean isMultiCompletable;
    protected volatile T result;
    protected volatile Exception exception;
    private volatile State state = State.INITIAL;

    /**
     * This method creates new {@link AbstractFuture<T>} to be run on specified executor single
     * or multiple times.
     *
     * @param executor           {@link Executor} instance
     * @param isMultiCompletable future can be run or completed multiple times, false otherwise
     */
    protected AbstractFuture(Executor executor, boolean isMultiCompletable) {
        super(executor);
        this.isMultiCompletable = isMultiCompletable;
    }

    /**
     * This method creates {@link AbstractFuture<T>} to be run on specified executor.
     *
     * @param executor {@link Executor} instance
     */
    protected AbstractFuture(Executor executor) {
        this(executor, false);
    }

    /**
     * Complete current {@link AbstractFuture<T>} with provided result. {@link AbstractFuture<T>} can
     * be completed multiple times in case it is created as multi completable.
     *
     * @param result value or null
     * @return true if future was successfully completed or false if it has been already completed or
     * canceled
     */
    @Override
    public synchronized boolean complete(T result) {
        return emit(result);
    }

    /**
     * Complete current {@link AbstractFuture<T>} with provided error. {@link AbstractFuture<T>} can
     * be completed multiple times in case it is created as multi completable.
     *
     * @param exception {@link Exception} instance
     * @return true if future was successfully completed or false if it has been already completed or
     * canceled
     */
    @Override
    public synchronized boolean complete(Exception exception) {
        return emit(exception);
    }

    @Override
    public boolean isMultiCompletable() {
        return isMultiCompletable;
    }

    /**
     * This method cancels current {@link AbstractFuture<T>} evaluation. Future state is changed to
     * {@link State#CANCELED} so it cannot be completed, submitted or res-submitted anymore.
     * If there is evaluation in progress then it also may be terminated. Canceled future is
     * unsubscribed from all subscribers.
     *
     * @param mayInterruptIfRunning terminate evaluation if it is true. In most cases it means
     *                              that current {@link Thread} is interrupted using method
     *                              {@link Thread#interrupt}.
     * @return true if future has been successfully canceled
     */
    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (setState(State.CANCELED)) {
            removeAllSubscribers();
            notifyAll();
            return true;
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        return state == State.CANCELED;
    }

    @Override
    public boolean isDone() {
        return state == State.COMPLETED;
    }

    /**
     * This method waits for the result. Calling {@link Thread} is blocked until {@link AbstractFuture<T>}
     * is completed or canceled.
     *
     * @return result
     * @throws ExecutionException   if error has been thrown during computation
     * @throws InterruptedException when operation has been interrupted
     */
    @Override
    public synchronized T get() throws ExecutionException, InterruptedException {
        if (state == State.COMPLETED) {
            return getResultOrException();
        } else if (state != State.CANCELED) {
            wait();
            if (state == State.COMPLETED) {
                return getResultOrException();
            }
        }
        throw new CancellationException();
    }


    /**
     * This method waits for the result. Calling {@link Thread} is blocked until {@link AbstractFuture<T>}
     * is completed, canceled or time is out.
     *
     * @param timeout value that represents duration
     * @param unit    specifies time units to measure duration
     * @return
     * @throws ExecutionException   when error has been thrown during computation
     * @throws InterruptedException when operation has been interrupted
     * @throws TimeoutException     when time is out
     */
    @Override
    public synchronized T get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        if (state == State.COMPLETED) {
            return getResultOrException();
        } else if (state != State.CANCELED) {
            long nanos = unit.toNanos(timeout);
            wait(nanos / NANOS_TO_MILLIS, (int) (nanos % NANOS_TO_MILLIS));
            if (state == State.COMPLETED) {
                return getResultOrException();
            } else if (state != State.CANCELED) {
                throw new TimeoutException();
            }
        }
        throw new CancellationException();
    }

    @Override
    public T result() {
        try {
            return get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized Subscription<T> accept(Executor executor, Consumer<T> consumer) {
        Subscriber subscriber = addSubscriber(executor, consumer);
        if (state == State.COMPLETED) {
            subscriber.accept(result, exception);
        }
        return subscriber;
    }

    @Override
    public synchronized Subscription<T> observe(Executor executor, Observer<T> observer) {
        Subscriber subscriber = addSubscriber(executor, observer);
        if (state == State.COMPLETED) {
            subscriber.accept(result, exception);
        }
        return subscriber;
    }

    @Override
    public <D> ObservableFuture<D> map(Executor executor, Transformer<T, D> transformer) {
        TransformingFuture<D, T> future = new TransformingFuture<>(this, executor, isMultiCompletable, transformer);
        observe(executor, future);
        return future;
    }

    @Override
    public <D> ObservableFuture<D> map(Transformer<T, D> transformer) {
        return map(getExecutor(), transformer);
    }

    /**
     * This methods emits new value to stream and changes current {@link AbstractFuture<T>} state to
     * {@link State#COMPLETED}. If current future is multi completable this method can be called
     * multiple times to change last emitted value.
     *
     * @param result value to be emitted
     * @return true if future has been successfully completed
     */
    @Override
    @CallSuper
    protected synchronized boolean emit(T result) {
        if (setState(State.COMPLETED)) {
            this.result = result;
            this.exception = null;
            emit(result, exception);
            notifyAll();
            return true;
        }
        return false;
    }

    /**
     * This methods emits error to stream and changes current {@link AbstractFuture<T>} state to
     * {@link State#COMPLETED}. If current future is multi completable this method can be called
     * multiple times to change last emitted value.
     *
     * @param e {@link Exception} instance
     * @return true is future has been successfully completed
     */
    @Override
    @CallSuper
    protected synchronized boolean emit(Exception e) {
        if (setState(State.COMPLETED)) {
            this.exception = e;
            emit(null, e);
            notifyAll();
            return true;
        }
        return false;
    }

    protected State getState() {
        return state;
    }

    /**
     * This method tries to change a state of current {@link AbstractFuture<T>}. It limits possible
     * transitions. If current state is {@link State#COMPLETED} it can not be changed to
     * {@link State#CANCELED} for example.
     *
     * @param newState state to be applied
     * @return true is state was changed, false otherwise
     */
    protected synchronized final boolean setState(State newState) {
        if (state == State.INITIAL) {
            if (newState == State.COMPLETING || newState == State.COMPLETED ||
                    newState == State.CANCELED) {
                state = newState;
                return true;
            }
        } else if (state == State.COMPLETING) {
            if (newState == State.COMPLETED || newState == State.CANCELED) {
                state = newState;
                return true;
            }
        } else if (state == State.COMPLETED && isMultiCompletable) {
            if (newState == State.INITIAL || newState == State.COMPLETED) {
                state = newState;
                return true;
            }
        }
        return false;
    }

    private T getResultOrException() throws ExecutionException {
        if (exception == null) {
            return result;
        } else {
            throw new ExecutionException(exception);
        }
    }

    /**
     * Possible states
     */
    protected enum State {
        /**
         * Initial state that is assigned to {@link AbstractFuture} when it has been created
         */
        INITIAL,

        /**
         * This state is assigned when future has been submitted for processing and it is in
         * progress
         */
        COMPLETING,

        /**
         * This state is assigned when processing has been finished and value or error has been
         * emitted
         */
        COMPLETED,

        /**
         * This state is assigned when {@link AbstractFuture} is canceled. It is terminal state
         * that can not be changed.
         */
        CANCELED
    }
}
