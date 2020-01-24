package com.testapp;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;

import com.asyncexecutor.AsyncExecutorService;
import com.asyncexecutor.ObservableFuture;
import com.asyncexecutor.implementation.AsyncContextWrapper;
import com.asyncexecutor.implementation.AsyncExecutor;
import com.asyncexecutor.implementation.CompletableFuture;
import com.asyncexecutor.implementation.CompositeFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4ClassRunner.class)
public class BasicTests extends AsyncContextWrapper {
    private final static int COUNT = 20000;
    private final Random random = new Random();
    private final String string = "test string";
    private final Integer number = 100;
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicLong executedCount = new AtomicLong();
    private AsyncExecutorService executor;
    private boolean equals;
    private Exception exception;

    public BasicTests() {
        super(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @Before
    public void newExecutor() {
        executor = new InstrumentedExecutor(new LinkedList<>(), 5, 100, TimeUnit.MILLISECONDS);
        executedCount.set(0);
        exception = null;
        equals = false;
        counter.set(0);
    }

    @After
    public void shutdownExecutor() {
        cancelSubscriptions();
        cancelFutures(true);
        executor.shutdownNow();
    }

    @Test
    public synchronized void simpleSubmit() throws Exception {
        ObservableFuture<String> future = executor.submit(() -> {
            Thread.sleep(100);
            return string;
        });
        addSubscription(future.accept(result -> {
            synchronized (this) {
                equals = result.equals(string);
                notifyAll();
            }
        }));
        wait();
        assertTrue(equals);
        assertEquals(string, future.get());
    }

    @Test(expected = ExecutionException.class)
    public synchronized void simpleSubmitException() throws Exception {
        ObservableFuture<String> future = executor.submit(() -> {
            Thread.sleep(100);
            throw new RuntimeException();
        });
        addSubscription(future.observe((result, e) -> {
            synchronized (this) {
                if (e != null) {
                    exception = e;
                    notifyAll();
                }
            }
        }));
        wait();
        assertNotNull(exception);
        future.get();
    }

    @Test(expected = CancellationException.class)
    public synchronized void simpleCancel() throws Exception {
        ObservableFuture<String> future = executor.submit(() -> {
            Thread.sleep(500);
            return string;
        });
        future.cancel(false);
        addSubscription(future.accept(result -> {
            synchronized (this) {
                equals = result.equals(string);
                notifyAll();
            }
        }));
        wait(1000);
        assertFalse(equals);
        assertTrue(future.isCancelled());
        future.get();
    }

    @Test
    public synchronized void complete1() throws Exception {
        ObservableFuture<String> future = executor.submit(() -> {
            Thread.sleep(500);
            return string;
        });
        future.complete(string + string);
        addSubscription(future.accept(result -> {
            synchronized (this) {
                equals = result.equals(string);
                notifyAll();
            }
        }));
        wait(1000);
        assertFalse(equals);
        assertEquals(future.get(), string + string);
    }

    @Test(expected = ExecutionException.class)
    public synchronized void complete2() throws Exception {
        ObservableFuture<String> future = executor.submit(() -> {
            Thread.sleep(500);
            return string;
        });
        future.complete(new RuntimeException());
        addSubscription(future.accept(result -> {
            synchronized (this) {
                equals = result.equals(string);
                notifyAll();
            }
        }));
        wait(1000);
        assertFalse(equals);
        future.get();
    }

    @Test
    public void multiCompletable() throws Exception {
        List<Integer> accepted = Collections.synchronizedList(new ArrayList<>(COUNT));
        List<Integer> list = new ArrayList<>(COUNT);
        CompletableFuture<Integer> future = new CompletableFuture<>(executor, true, counter::incrementAndGet);
        addSubscription(future.accept(accepted::add));
        for (int i = 0; i < COUNT; i++) {
            list.add(future.submit().get());
        }
        assertTrue(executor.awaitCompletion());
        Collections.sort(accepted, Integer::compareTo);
        assertEquals(COUNT, accepted.size());
        assertEquals(COUNT, list.size());
        assertEquals(list, accepted);
    }

    @Test
    public void shutdown() throws Exception {
        List<String> list = Collections.synchronizedList(new ArrayList<>(COUNT));
        for (int i = 0; i < 2000; i++) {
            ObservableFuture<String> future = executor.submit(() -> {
                Thread.sleep(random.nextInt(50));
                return String.valueOf(counter.incrementAndGet());
            });
            addSubscription(future.accept(list::add));
        }
        executor.shutdown();
        assertTrue(executor.isShutdown());
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(executor.isTerminated());
    }

    @Test
    public void awaitCompletion() throws Exception {
        CompositeFuture<Integer> composite = new CompositeFuture<>();
        List<Integer> accepted = Collections.synchronizedList(new ArrayList<>(COUNT));
        for (int i = 0; i < COUNT; i++) {
            ObservableFuture<Integer> future = executor.submit(counter::incrementAndGet);
            addSubscription(future.accept(accepted::add));
            composite.addFuture(future);
        }
        assertTrue(executor.awaitCompletion());
        assertTrue(composite.isDone());
        Set<Integer> set = new TreeSet<>(composite.get());
        assertEquals(COUNT, set.size());
        assertEquals(COUNT, accepted.size());
    }

    @Test
    public synchronized void simpleMap() throws Exception {
        List<Integer> list = Collections.synchronizedList(new ArrayList<>(COUNT));
        for (int i = 0; i < COUNT; i++) {
            ObservableFuture<Integer> future =
                    executor.submit(() -> String.valueOf(counter.incrementAndGet()))
                            .map((value, exception) -> Integer.valueOf(value));
            addSubscription(future.accept(list::add));
        }
        assertTrue(executor.awaitCompletion());
        Set<Integer> set = new TreeSet<>(list);
        assertEquals(COUNT, set.size());
    }

    @Test
    public synchronized void oddNumbers() throws Exception {
        List<Integer> list = Collections.synchronizedList(new ArrayList<>(COUNT));
        AtomicInteger exceptionCounter = new AtomicInteger();
        for (int i = 0; i < COUNT; i++) {
            ObservableFuture<Integer> future =
                    executor.submit(() -> {
                        int value = counter.incrementAndGet();
                        if (value % 2 == 0) {
                            return value;
                        } else {
                            throw new RuntimeException();
                        }
                    });
            addSubscription(future.observe((result, e) -> {
                if (e == null) {
                    list.add(result);
                } else {
                    exceptionCounter.incrementAndGet();
                }
            }));
        }
        assertTrue(executor.awaitCompletion());
        Set<Integer> set = new TreeSet<>(list);
        assertEquals(COUNT / 2, set.size());
        assertEquals(COUNT / 2, exceptionCounter.get());
    }

    private class InstrumentedExecutor extends AsyncExecutor {
        InstrumentedExecutor(Queue<Runnable> queue, int maxWorkers, long timeout, TimeUnit unit) {
            super(queue, maxWorkers, timeout, unit);
        }

        @Override
        protected void onInterrupted(Worker worker) {
            super.onInterrupted(worker);
            executedCount.addAndGet(worker.getExecutedCount());
        }

        @Override
        protected void onShutdown(Worker worker) {
            super.onShutdown(worker);
            executedCount.addAndGet(worker.getExecutedCount());
        }
    }
}
