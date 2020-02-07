/*
 * Copyright (c) 2020 Pavel A. Sokolov <pavel.zeppa@yandex.ru>
 */
package com.asyncexecutor.implementation;

import androidx.annotation.CallSuper;

import com.asyncexecutor.AsyncExecutorService;
import com.asyncexecutor.ObservableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class extends {@link AbstractExecutorService} and implements {@link AsyncExecutorService}. It
 * uses pool of thread to execute submitted tasks. Threads are reused and released only after certain
 * timeout. First thread is not released at all so there is at least one thread ready to process
 * submitted tasks.
 */
public class AsyncExecutor extends AbstractExecutorService implements AsyncExecutorService {
    protected final static int NANOS_TO_MILLIS = 1000000;
    private final static AtomicLong groupCounter = new AtomicLong();
    private final AtomicLong workerCounter = new AtomicLong();
    private final ThreadGroup threadGroup;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final Queue<Runnable> queue;
    private final Queue<Worker> pollingWorkers;
    private final Set<Worker> workers;
    private final int maxWorkers;
    private final TimeUnit unit;
    private final long timeout;

    /**
     * This method create new {@link AsyncExecutor} instance.
     *
     * @param name       name of this executor to be used as part of {@link Thread} name
     * @param queue      to store tasks in case ot there is no free {@link Thread} is ready to start processing
     * @param maxWorkers maximal number of {@link Thread} to be instantiated
     * @param timeout    polling timeout that specifies amount of time in {@link TimeUnit} that limits
     *                   {@link Thread} lifetime if there are no tasks
     * @param unit       {@link TimeUnit} timeout units
     */
    public AsyncExecutor(String name, Queue<Runnable> queue, int maxWorkers, long timeout, TimeUnit unit) {
        this.queue = queue;
        this.maxWorkers = maxWorkers;
        this.timeout = timeout;
        this.unit = unit;
        threadGroup = new ThreadGroup(name + "-" + groupCounter.getAndIncrement());
        workers = new TreeSet<>();
        pollingWorkers = new LinkedList<>();
    }

    public AsyncExecutor(Queue<Runnable> queue, int maxWorkers, long timeout, TimeUnit unit) {
        this("async-executor", queue, maxWorkers, timeout, unit);
    }

    protected ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    protected <T> AsyncTask<T> newTaskFor(Runnable runnable, T value) {
        return new AsyncTask<>(this, runnable, value);
    }

    @Override
    protected <T> AsyncTask<T> newTaskFor(Callable<T> callable) {
        return new AsyncTask<>(this, callable);
    }

    @Override
    public final void execute(Runnable runnable) {
        if (isRunning.get()) {
            try {
                synchronized (this) {
                    Worker worker = getWorker();
                    if (worker == null) {
                        queue.add(runnable);
                    } else {
                        worker.post(runnable);
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalStateException("executor is shutting down");
        }
    }

    @Override
    public ObservableFuture<?> submit(Runnable task) {
        AsyncTask<?> runnable = newTaskFor(task, null);
        execute(runnable);
        return runnable;
    }

    @Override
    public <T> ObservableFuture<T> submit(Runnable task, T result) {
        AsyncTask<T> runnable = newTaskFor(task, result);
        execute(runnable);
        return runnable;
    }

    @Override
    public <T> ObservableFuture<T> submit(Callable<T> task) {
        AsyncTask<T> runnable = newTaskFor(task);
        execute(runnable);
        return runnable;
    }

    @CallSuper
    @Override
    public void shutdown() {
        try {
            if (isRunning.compareAndSet(true, false)) {
                shutdownPollingWorkers();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @CallSuper
    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> list = Collections.emptyList();
        try {
            if (isRunning.compareAndSet(true, false)) {
                list = shutdownPollingWorkers();
                threadGroup.interrupt();
            }
        } catch (InterruptedException e) {
        }
        return list;
    }

    @Override
    public boolean isShutdown() {
        return !isRunning.get();
    }

    @Override
    public synchronized boolean isTerminated() {
        return !isRunning.get() && workers.isEmpty();
    }

    @Override
    public final synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (isRunning.get()) {
            throw new IllegalStateException("executor is running");
        } else if (workers.isEmpty()) {
            return true;
        } else {
            long nanos = unit.toNanos(timeout);
            wait(nanos / NANOS_TO_MILLIS, (int) (nanos % NANOS_TO_MILLIS));
            return workers.isEmpty();
        }
    }

    @Override
    public final synchronized boolean awaitCompletion() throws InterruptedException {
        if (isRunning.get()) {
            if (isAllWorkersPolling()) {
                return true;
            } else {
                wait();
                return isAllWorkersPolling();
            }
        } else {
            throw new IllegalStateException("executor is shutdown");
        }
    }

    private synchronized List<Runnable> shutdownPollingWorkers() throws InterruptedException {
        List<Runnable> result = new ArrayList<>(queue);
        while (!pollingWorkers.isEmpty()) {
            pollingWorkers.poll().shutdown();
        }
        queue.clear();
        return result;
    }

    /**
     * This method gets new {@link Worker} from pool or creates new one if limit is not reached.
     *
     * @return {@link Worker} instance
     */
    private synchronized Worker getWorker() {
        Worker worker = pollingWorkers.poll();
        if (worker == null && workers.size() < maxWorkers) {
            worker = new Worker(threadGroup, workerCounter.incrementAndGet(), workers.isEmpty());
            workers.add(worker);
            worker.start();
        }
        return worker;
    }

    private void tryWakeUpSingleWorker() {
        Worker worker = getWorker();
        if (worker != null) {
            worker.wakeup();
        }
    }

    /**
     * This method is called when {@link Worker} should be released as a result of polling timeout
     * or interruption. All unprocessed tasks are moved to the global queue to be submitted for
     * processing again.
     *
     * @param worker {@link Worker} instance.
     */
    private synchronized void releaseWorker(Worker worker) {
        workers.remove(worker);
        if (workers.isEmpty() || isAllWorkersPolling()) {
            notifyAll();
        }
        List<Runnable> runnables = worker.drainRunnables();
        if (!runnables.isEmpty()) {
            queue.addAll(runnables);
            tryWakeUpSingleWorker();
        }
    }

    protected final synchronized boolean isAllWorkersPolling() {
        return pollingWorkers.size() == workers.size();
    }

    /**
     * This method is called by {@link Worker} when its command queue is empty and it is about to
     * start polling.
     *
     * @param worker {@link Worker} instance
     * @return true if new commands are posted to {@link Worker} local queue, false otherwise
     * @throws InterruptedException
     */
    @CallSuper
    protected synchronized boolean onStartPolling(Worker worker) throws InterruptedException {
        if (isRunning.get()) {
            if (queue.isEmpty()) {
                pollingWorkers.add(worker);
                if (isAllWorkersPolling()) {
                    notifyAll();
                }
            } else {
                int share = queue.size() > maxWorkers ? queue.size() / maxWorkers : 1;
                while (!queue.isEmpty() && !worker.isFull() && share > 0) {
                    worker.post(queue.poll());
                    share--;
                }
                return true;
            }
        } else {
            worker.shutdown();
        }
        return false;
    }

    /**
     * This method is called by {@link Worker} when polling timeout happens. There are no commands to
     * be handled in other words. It initiates {@link Worker} shutdown to release it.
     *
     * @param worker {@link Worker} instance
     * @throws InterruptedException
     */
    @CallSuper
    protected synchronized void onPollingTimeout(Worker worker) throws InterruptedException {
        pollingWorkers.remove(worker);
        worker.shutdown();
    }

    /**
     * This method is called when {@link Worker} is interrupted and it is about to terminate.
     * It removes {@link Worker} from pool and releases it for garbage colleciton.
     *
     * @param worker {@link Worker} instance
     */
    @CallSuper
    protected void onInterrupted(Worker worker) {
        releaseWorker(worker);
    }

    /**
     * This method is called when {@link Worker} is handled all commands and it is about to
     * terminate since polling timeout occurs.
     *
     * @param worker {@link Worker} instance
     */
    @CallSuper
    protected void onShutdown(Worker worker) {
        releaseWorker(worker);
    }

    /**
     * This method is called when submitted task has thrown an error.
     *
     * @param worker {@link Worker} instance
     * @param e      {@link Throwable} instance
     */
    @CallSuper
    protected void onError(Worker worker, Throwable e) {

    }

    @Override
    public String toString() {
        return "AsyncExecutor{" + threadGroup.getName() + "}";
    }

    /**
     * This enum describes commands to be processed by {@link Worker}
     */
    protected enum RequestType {
        /**
         * Execute {@link Runnable}
         */
        EXECUTE,
        /**
         * Shutdown execution and terminate
         */
        SHUTDOWN,
        /**
         * Check global queue for new tasks
         */
        WAKEUP
    }

    protected class Request {
        final RequestType type;
        final Runnable runnable;

        Request(RequestType type, Runnable runnable) {
            this.type = type;
            this.runnable = runnable;
        }

        Request(RequestType type) {
            this.type = type;
            runnable = null;
        }
    }

    /**
     * This class extends {@link Thread} class and implements simple api to submit tasks.
     * It has small internal queue to accumulate {@link Runnable} to be executed. This queue helps to
     * avoid outer class locking.
     */
    protected class Worker extends Thread implements Comparable<Worker> {
        // EXECUTE and SHUTDOWN at the same time requires capacity of two items at least
        final BlockingQueue<Request> requests = new ArrayBlockingQueue<>(4);
        final long id;
        final boolean isInfinitePolling;
        // count of processed runnables
        volatile long executedCount;
        // count of thrown errors
        volatile long errorsCount;
        // count of onStartPolling method calls that filled internal queue with new commands
        volatile long transferCount;

        Worker(ThreadGroup group, long id, boolean isInfinitePolling) {
            super(group, group.getName() + "-worker-" + id);
            this.isInfinitePolling = isInfinitePolling;
            this.id = id;
        }

        boolean isFull() {
            return requests.remainingCapacity() == 0;
        }

        void post(Runnable runnable) throws InterruptedException {
            requests.put(new Request(RequestType.EXECUTE, runnable));
        }

        void shutdown() throws InterruptedException {
            requests.put(new Request(RequestType.SHUTDOWN));
        }

        boolean wakeup() {
            return requests.offer(new Request(RequestType.WAKEUP));
        }

        List<Runnable> drainRunnables() {
            List<Runnable> list = new ArrayList<>(requests.size());
            while (!requests.isEmpty()) {
                Request request = requests.poll();
                if (request.type == RequestType.EXECUTE) {
                    list.add(request.runnable);
                }
            }
            return list;
        }

        public long getTransferRate() {
            return (100 * queue.size() * transferCount) / executedCount;
        }

        public long getExecutedCount() {
            return executedCount;
        }

        @Override
        public final void run() {
            while (!isInterrupted()) {
                try {
                    if (requests.isEmpty()) {
                        if (onStartPolling(this)) {
                            transferCount++;
                        }
                    }
                    Request request = isInfinitePolling ? requests.take() : requests.poll(timeout, unit);
                    if (request == null) {
                        onPollingTimeout(this);
                    } else if (request.type == RequestType.EXECUTE) {
                        request.runnable.run();
                        executedCount++;
                    } else if (request.type == RequestType.SHUTDOWN) {
                        break;
                    }
                } catch (InterruptedException e) {
                    onInterrupted(this);
                    return;
                } catch (Throwable e) {
                    errorsCount++;
                    onError(this, e);
                }
            }
            if (isInterrupted()) {
                onInterrupted(this);
            } else {
                onShutdown(this);
            }
        }

        @Override
        public int compareTo(Worker o) {
            return Long.compare(id, o.id);
        }

        @Override
        public String toString() {
            return "Worker{" +
                    "id=" + id +
                    ", executedCount=" + executedCount +
                    ", errorsCount=" + errorsCount +
                    ", transferCount=" + transferCount +
                    '}';
        }
    }

    private static class AsyncTask<T> extends CompletableFuture<T> implements RunnableFuture<T> {
        AsyncTask(Executor executor, Callable<T> callable) {
            super(executor, callable);
        }

        AsyncTask(Executor executor, Runnable runnable, T result) {
            super(executor, runnable, result);
        }
    }
}
