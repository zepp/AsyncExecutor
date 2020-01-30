/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor;

/**
 * This interface describes a type to perform some sort of operation that accepts value or error as
 * input arguments and returns another value or throws exception.
 *
 * @param <T> input value type
 * @param <R> output value type
 */
@FunctionalInterface
public interface Transformer<T, R> {
    R transform(T value, Exception exception) throws Exception;
}
