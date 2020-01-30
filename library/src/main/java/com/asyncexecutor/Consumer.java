/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor;

@FunctionalInterface
public interface Consumer<T> {

    /**
     * Handle given argument in any way
     *
     * @param result the input argument
     */
    void accept(T result);
}
