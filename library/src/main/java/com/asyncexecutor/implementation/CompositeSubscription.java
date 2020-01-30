/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor.implementation;

import com.asyncexecutor.Observable;
import com.asyncexecutor.Subscription;

import java.util.Set;
import java.util.TreeSet;

/**
 * This class implements {@link Subscription<T>} interface to represents list of {@link Subscription<T>}
 * as composite or single instance in other words. It helps to manage a list of subscriptions in current
 * context. {@link CompositeSubscription<T>} can be added to the another {@link CompositeSubscription<T>}
 * to build a tree of subscriptions that can be canceled at once.
 *
 * @param <T> value type
 */
public class CompositeSubscription<T> implements Subscription<T> {
    protected final Set<Subscription<T>> subscriptions = new TreeSet<>();

    public CompositeSubscription() {
    }

    @Override
    public long getId() {
        return 0;
    }

    @Override
    public Observable<T> getObservable() {
        return null;
    }

    /**
     * This methods adds new subscription to the list
     *
     * @param subscription {@link Subscription<T>} instance
     */
    public synchronized void addSubscription(Subscription<T> subscription) {
        subscriptions.add(subscription);
    }

    /**
     * This method cancels all added subscriptions and clears the list
     */
    @Override
    public synchronized void cancel() {
        for (Subscription<?> subscription : subscriptions) {
            subscription.cancel();
        }
        subscriptions.clear();
    }

    @Override
    public int compareTo(Subscription<T> o) {
        return 1;
    }

    public synchronized boolean isEmpty() {
        return subscriptions.isEmpty();
    }
}
