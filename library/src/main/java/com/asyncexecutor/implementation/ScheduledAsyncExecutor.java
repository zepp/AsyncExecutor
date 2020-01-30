/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor.implementation;

import com.asyncexecutor.ScheduledAsyncExecutorService;
import com.asyncexecutor.ScheduledObservableFuture;
import com.asyncexecutor.ScheduledType;

import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledAsyncExecutor extends AsyncExecutor implements ScheduledAsyncExecutorService {
    private final AtomicInteger schedulerCounter = new AtomicInteger();
    private volatile Scheduler scheduler;

    public ScheduledAsyncExecutor(String name, Queue<Runnable> queue, int maxWorkers, long timeout, TimeUnit unit) {
        super(name, queue, maxWorkers, timeout, unit);
    }

    private Scheduler getScheduler() {
        if (scheduler == null) {
            synchronized (Scheduler.class) {
                if (scheduler == null) {
                    scheduler = new Scheduler(getThreadGroup(), schedulerCounter.incrementAndGet());
                    scheduler.start();
                }
            }
        }
        return scheduler;
    }

    private void releaseScheduler(Scheduler scheduler) {
        synchronized (Scheduler.class) {
            if (scheduler.equals(this.scheduler)) {
                this.scheduler = null;
            }
        }
    }

    private void shutdownScheduler() {
        synchronized (Scheduler.class) {
            if (scheduler != null) {
                scheduler.interrupt();
            }
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        shutdownScheduler();
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdownScheduler();
        return super.shutdownNow();
    }

    private void checkShutdown() throws IllegalStateException {
        if (isShutdown()) {
            throw new IllegalStateException("executor is shutdown");
        }
    }

    @Override
    public ScheduledObservableFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        checkShutdown();
        return getScheduler().schedule(new AsyncScheduledFuture<>(this, command, unit.toNanos(delay)));
    }

    @Override
    public <V> ScheduledObservableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        checkShutdown();
        return getScheduler().schedule(new AsyncScheduledFuture<>(this, callable, unit.toNanos(delay)));
    }

    @Override
    public ScheduledObservableFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        checkShutdown();
        return getScheduler().schedule(new AsyncScheduledFuture<>(this, command, ScheduledType.FIXED_RATE, initialDelay, period, unit));
    }

    @Override
    public ScheduledObservableFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        checkShutdown();
        return getScheduler().schedule(new AsyncScheduledFuture<>(this, command, ScheduledType.FIXED_DELAY, initialDelay, delay, unit));
    }

    @Override
    public <V> ScheduledObservableFuture<V> scheduleAtFixedRate(Callable<V> callable, long initialDelay, long period, TimeUnit unit) {
        checkShutdown();
        return getScheduler().schedule(new AsyncScheduledFuture<>(this, callable, ScheduledType.FIXED_RATE, initialDelay, period, unit));
    }

    @Override
    public <V> ScheduledObservableFuture<V> scheduleWithFixedDelay(Callable<V> callable, long initialDelay, long delay, TimeUnit unit) {
        checkShutdown();
        return getScheduler().schedule(new AsyncScheduledFuture<>(this, callable, ScheduledType.FIXED_DELAY, initialDelay, delay, unit));
    }

    private interface ScheduledRunnableFuture<V> extends ScheduledObservableFuture<V>, Runnable {
    }

    private class AsyncScheduledFuture<T> extends ScheduledCompletableFuture<T> implements ScheduledRunnableFuture<T> {
        AsyncScheduledFuture(Executor executor, Callable<T> callable, long scheduledNanos) {
            super(executor, callable, scheduledNanos);
        }

        AsyncScheduledFuture(Executor executor, Runnable runnable, long scheduledNanos) {
            super(executor, runnable, scheduledNanos);
        }

        AsyncScheduledFuture(Executor executor, Callable<T> callable, ScheduledType type, long initialDelay, long period, TimeUnit unit) {
            super(executor, callable, type, initialDelay, period, unit);
        }

        AsyncScheduledFuture(Executor executor, Runnable runnable, ScheduledType type, long initialDelay, long period, TimeUnit unit) {
            super(executor, runnable, type, initialDelay, period, unit);
        }

        @Override
        protected void reschedule() {
            if (getState() == State.CANCELED) {
                getScheduler().cancel(this);
            } else if (getType() != ScheduledType.SINGLE_SHOT) {
                super.reschedule();
                getScheduler().schedule(this);
            }
        }
    }

    /**
     * This class is execution scheduler that is built on top of {@link Object#wait(long, int)} and
     * {@link Object#notify()} functions and {@link PriorityQueue}. {@link ScheduledRunnableFuture}
     * instance is enqueued into the priority queue that sorts items according to scheduled execution
     * time then scheduler loop is notified. {@link Thread} wakes up and checks an item from the head
     * of the queue to obtain wait time using {@link java.util.concurrent.ScheduledFuture#getDelay(TimeUnit)}
     * method.
     */
    private class Scheduler extends Thread {
        private final Queue<ScheduledRunnableFuture<?>> queue = new PriorityQueue<>(10,
                (o1, o2) -> Long.compare(o1.getScheduledTime(TimeUnit.NANOSECONDS), o2.getScheduledTime(TimeUnit.NANOSECONDS)));
        private final AtomicBoolean isReleased = new AtomicBoolean();

        Scheduler(ThreadGroup group, int id) {
            super(group, group.getName() + "-scheduler-" + id);
            setPriority(Thread.MAX_PRIORITY);
        }

        /**
         * Scheduler loop that waits most of the time
         */
        @Override
        public synchronized void run() {
            try {
                while (!isInterrupted()) {
                    long nanos;
                    if (queue.isEmpty()) {
                        if (isReleased.get()) {
                            return;
                        } else if (getTimeout() > 0) {
                            nanos = getUnit().toNanos(getTimeout());
                        } else {
                            nanos = TimeUnit.SECONDS.toNanos(30);
                        }
                    } else {
                        nanos = queue.element().getDelay(TimeUnit.NANOSECONDS);
                    }
                    if (nanos > 0) {
                        wait(nanos / NANOS_TO_MILLIS, (int) (nanos % NANOS_TO_MILLIS));
                    } else {
                        execute(queue.poll());
                        continue;
                    }
                    if (queue.isEmpty()) {
                        if (isReleased.compareAndSet(false, true)) {
                            releaseScheduler(this);
                        }
                    } else if (queue.element().getDelay(TimeUnit.NANOSECONDS) <= 0) {
                        execute(queue.poll());
                    }
                }
            } catch (InterruptedException e) {
                releaseScheduler(this);
            }
        }

        /**
         * Removes item from internal queue.
         *
         * @param future {@link ScheduledRunnableFuture} instance
         */
        synchronized void cancel(ScheduledRunnableFuture future) {
            if (future.equals(queue.peek())) {
                notify();
            }
            queue.remove(future);
        }

        /**
         * Puts item into internal queue to schedule execution.
         *
         * @param future {@link ScheduledRunnableFuture} instance
         * @param <T>    emitted value type
         * @return same {@link ScheduledRunnableFuture} instance
         */
        synchronized <T> ScheduledRunnableFuture<T> schedule(ScheduledRunnableFuture<T> future) {
            queue.add(future);
            if (future.equals(queue.peek())) {
                notify();
            }
            return future;
        }
    }
}
