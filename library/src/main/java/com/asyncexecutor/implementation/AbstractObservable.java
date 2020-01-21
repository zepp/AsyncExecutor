package com.asyncexecutor.implementation;

import androidx.annotation.CallSuper;

import com.asyncexecutor.Consumer;
import com.asyncexecutor.Observable;
import com.asyncexecutor.Observer;
import com.asyncexecutor.Subscription;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class that implements {@link Observable<T>} interface.
 *
 * @param <T> emitted value type
 */
public abstract class AbstractObservable<T> implements Observable<T> {
    // counter to generate subscription id
    private final static AtomicLong lastId = new AtomicLong();
    /**
     * executor to run {@link Observable<T>} and {@link Consumer<T>} handlers by default
     */
    private final Executor executor;
    // Set of subscriptions
    private final Set<Subscriber> subscribers = new TreeSet<>();

    protected AbstractObservable(Executor executor) {
        this.executor = executor;
    }

    @Override
    public Subscription<T> accept(Consumer<T> consumer) {
        return addSubscriber(executor, consumer);
    }

    @Override
    public Subscription<T> accept(Executor executor, Consumer<T> consumer) {
        return addSubscriber(executor, consumer);
    }

    @Override
    public Subscription<T> observe(Observer<T> observer) {
        return addSubscriber(executor, observer);
    }

    @Override
    public Subscription<T> observe(Executor executor, Observer<T> observer) {
        return addSubscriber(executor, observer);
    }

    /**
     * This method creates new {@link Subscriber} from {@link Observable<T>} to encapsulate
     * exact handler and adds it to subscribers list.
     *
     * @param executor {@link Executor} to run the {@link Observable#accept}
     * @param observer {@link Observable<T>} itself
     * @return new instance
     */
    protected final synchronized Subscriber addSubscriber(Executor executor, Observer<T> observer) {
        Subscriber subscriber = new Subscriber(lastId.incrementAndGet(), executor, observer);
        subscribers.add(subscriber);
        return onObserverSubscribed(subscriber, subscribers.size() == 1);
    }

    /**
     * This method creates new {@link Subscriber} from {@link Consumer<T>} to encapsulate
     * exact handler and adds it to subscribers list.
     *
     * @param executor {@link Executor} to run the {@link Consumer#accept}
     * @param consumer {@link Consumer<T>} itself
     * @return new instance
     */
    protected final synchronized Subscriber addSubscriber(Executor executor, Consumer<T> consumer) {
        Subscriber subscriber = new Subscriber(lastId.incrementAndGet(), executor, consumer);
        subscribers.add(subscriber);
        return onObserverSubscribed(subscriber, subscribers.size() == 1);
    }

    /**
     * This method unsubscribes {@link Subscriber} from current {@link Observable<T>} and removes it
     * from subscribers list
     *
     * @param subscriber {@link Subscriber} instance
     * @return same {@link Subscriber} instance
     */
    protected final synchronized Subscriber removeSubscriber(Subscriber subscriber) {
        if (subscribers.remove(subscriber)) {
            return onObserverUnsubscribed(subscriber, subscribers.isEmpty());
        } else {
            return subscriber;
        }
    }

    /**
     * This method removes all subscribers from current {@link AbstractObservable<T>} and makes them
     * available for garbage collection.
     */
    protected final synchronized void removeAllSubscribers() {
        for (Subscriber subscriber : subscribers) {
            subscriber.onUnsubscribed();
        }
        subscribers.clear();
    }

    /**
     * This is a basic method to emit new value or error that will be delivered to all {@link Subscriber}.
     * If error is not null then actual value of <code>result</code> does not matter. It is not
     * delivered to {@link Consumer<T>} subscribers in such case.
     *
     * @param result value
     * @param e      {@link Exception} instance
     * @return
     */
    protected final synchronized boolean emit(T result, Exception e) {
        for (Subscriber subscriber : subscribers) {
            subscriber.accept(result, e);
        }
        return !subscribers.isEmpty();
    }

    /**
     * This method is called when new {@link Subscriber} is subscribed. It provides a way to perform
     * some kind of initialization or start computation on first subscription.
     *
     * @param subscriber {@link Subscriber} instance
     * @param isFirst true if it is first subscriber, false otherwise
     * @return
     */
    @CallSuper
    protected Subscriber onObserverSubscribed(Subscriber subscriber, boolean isFirst) {
        subscriber.onSubscribed();
        return subscriber;
    }

    /**
     * This method is called when {@link Subscriber} is unsubscribed. It provides a way to release
     * some resources when last subscription is canceled.
     *
     * @param subscriber {@link Subscriber} instance
     * @param isLast true if it is last subscriber, false otherwise
     * @return
     */
    @CallSuper
    protected Subscriber onObserverUnsubscribed(Subscriber subscriber, boolean isLast) {
        subscriber.onUnsubscribed();
        return subscriber;
    }

    /**
     * This method emits new value to be processed by all subscribers
     *
     * @param result value
     * @return true is case of success, false otherwise
     */
    protected boolean emit(T result) {
        return emit(result, null);
    }

    /**
     * This methods emits new error to be processed only by {@link Observer<T>} subscribers
     *
     * @param e {@link Exception} instance
     * @return true is case of success, false otherwise
     */
    protected boolean emit(Exception e) {
        return emit(null, e);
    }

    /**
     * Getter to return {@link Executor} of current {@link AbstractObservable<T>}
     *
     * @return
     */
    protected Executor getExecutor() {
        return executor;
    }

    /**
     * This class is a wrapper around the real {@link Consumer<T>} or {@link Observable<T>}. It
     * encapsulates real handler and provides unified interface to work with subscriber.
     */
    protected class Subscriber implements Subscription<T> {
        final long id;
        final Executor executor;
        final Consumer<T> consumer;
        final Observer<T> observer;
        final AtomicBoolean isSubscribed = new AtomicBoolean();

        private Subscriber(long id, Executor executor, Consumer<T> consumer) {
            this.id = id;
            this.executor = executor;
            this.consumer = consumer;
            this.observer = null;
        }

        private Subscriber(long id, Executor executor, Observer<T> observer) {
            this.id = id;
            this.executor = executor;
            this.observer = observer;
            this.consumer = null;
        }

        private void onSubscribed() {
            if (isSubscribed.compareAndSet(false, true) && observer != null) {
                observer.onSubscribed(this);
            }
        }

        private void onUnsubscribed() {
            if (isSubscribed.compareAndSet(true, false) && observer != null) {
                observer.onUnsubscribed(this);
            }
        }

        void accept(T result, Exception exception) {
            if (observer == null) {
                if (exception == null) {
                    executor.execute(() -> {
                        if (isSubscribed.get()) {
                            consumer.accept(result);
                        }
                    });
                }
            } else {
                executor.execute(() -> {
                    if (isSubscribed.get()) {
                        observer.accept(result, exception);
                    }
                });
            }
        }

        @Override
        public long getId() {
            return id;
        }

        @Override
        public void cancel() {
            removeSubscriber(this);
        }

        @Override
        public Observable<T> getObservable() {
            return AbstractObservable.this;
        }

        @Override
        public int compareTo(Subscription<T> o) {
            return Long.compare(id, o.getId());
        }
    }
}
