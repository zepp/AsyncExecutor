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
