package com.asyncexecutor.implementation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class implements {@link Future} interface to build a composite of {@link Future}. It keeps
 * a list of {@link Future} instances to delegate methods calls to these items. For example a bunch
 * of operations can be started and canceled at once when context is about to be destroyed.
 *
 * @param <T> value type
 */
public class CompositeFuture<T> implements Future<List<T>> {
    private final List<Future<T>> futures = new CopyOnWriteArrayList<>();

    /**
     * This method adds {@link Future<T>} to the list
     *
     * @param future {@link Future<T>} instance
     * @return same {@link Future<T>} instance
     */
    public Future<T> addFuture(Future<T> future) {
        futures.add(future);
        return future;
    }

    /**
     * This method clears the list
     */
    public void clearFutures() {
        futures.clear();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean result = true;
        for (Future<T> future : futures) {
            result &= future.cancel(mayInterruptIfRunning);
        }
        return result;
    }

    @Override
    public boolean isCancelled() {
        for (Future<T> future : futures) {
            if (!future.isCancelled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isDone() {
        for (Future<T> future : futures) {
            if (!future.isDone()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<T> get() throws ExecutionException, InterruptedException {
        List<T> result = new ArrayList<>(futures.size());
        for (Future<T> future : futures) {
            result.add(future.get());
        }
        return result;
    }

    @Override
    public List<T> get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        List<T> result = new ArrayList<>(futures.size());
        long nanos = unit.toNanos(timeout);
        for (Future<T> future : futures) {
            if (nanos > 0) {
                long n1 = System.nanoTime();
                result.add(future.get(nanos, TimeUnit.NANOSECONDS));
                nanos -= System.nanoTime() - n1;
            } else {
                throw new TimeoutException();
            }
        }
        return result;
    }
}
