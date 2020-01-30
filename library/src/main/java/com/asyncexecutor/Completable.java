/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor;

/**
 * This interface describes a type that provides a way to complete some operation manually.
 *
 * @param <T> type of result
 */
public interface Completable<T> {
    /**
     * Complete operation successfully
     *
     * @param result value or null
     * @return {@code true} is operation status is changed, {@code false} if operation can
     * not be completed or has been already completed.
     */
    boolean complete(T result);

    /**
     * Complete operation with error
     *
     * @param e error
     * @return {@code true} is operation status is changed, {@code false} if operation can
     * not be completed or has been already completed.
     */
    boolean complete(Exception e);

    /**
     * Getter that returns type of operation
     *
     * @return {@code true} if operation can be completed multiple times or {@code false}
     * if operation can be completed only a once
     */
    boolean isMultiCompletable();
}
