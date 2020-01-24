# AsyncExecutor
Main goal of this pet project is attempt to implement `java.util.concurrent.CompletableFuture` from scratch. It is not intended to re-implement it completely since it is kind of educational project for me.

So there are following features available:
* `AsyncExecutor` that maintains a pool of threads to run supplied `Runnable` or `Callable`
* `CompletableFuture` that enriches standard `Future` interface with a bunch of new methods
* `CompositeFuture` and `CompositeSubscription` to main subscriptions and futures in the current context
* `AsyncContextWrapper` that extends `ContextWrapper` to simplify asynchronous operation in the current context

## AsyncExecutorService & AsyncExecutor

`AsyncExecutorService` extends standard `ExecutorService` interface to improve current api and add new methods. It is implemented by `AsyncExecutor` class that extends `AbstractExecutor`. 

`AsyncExecutor` constructor is similar to `ThreadPoolExecutor` constructor in general but you can not specify core pool size. 

```java
AsyncExecutor(String name, Queue<Runnable> queue, int maxWorkers, long timeout, TimeUnit unit);
```

Core pool size equals to one by default and it can not be changed. Also simple `Queue` is accepted as argument to keep submitted requests. It gives a hint that `AsyncExecutor` and `ThreadPoolExecutor` internal design differs a lot.  

`AsyncExecutor` also uses pool of threads to run submitted requests as `ThreadPoolExecutor` does. Pool size is adjusted during runtime to maintain optimal number of threads. It works in following way `Thread` that does not have a request to be processed for the certain period of time is released.  

```java
public interface AsyncExecutorService {
  <T> ObservableFuture<T> submit(Callable<T> task);

  <T> ObservableFuture<T> submit(Runnable task, T result);

  ObservableFuture<?> submit(Runnable task);
}
```

Overloaded `AsyncExecutorService::submit` method returns `CompletableFuture`. Internally it calls `execute` method to complete its job.

Every time new request is submitted polling `Thread` is put to use or new `Thread` is allocated if `maxWorkers` number is not reached (see `execute` and `getWorker` methods for details). Every `Thread` has its own internal queue to keep incoming requests and avoid frequent outer class locking. If `Thread` is interrupted by `Thread::interrupt` method all unprocessed requests are enqueued to global queue to be processed by a new `Thread`.       

## CompletableFuture

`CompletableFuture` extends `AbstractFuture` class that implements `ObservableFuture` interface. `ObservableFuture` combines following interfaces: 
* `Future`
* `Completable`
* `Observable`

`Future` represents the result of an asynchronous computation (detailed [information](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html)). It is standard interface from JDK.

`Completable` represents interface of some operation to be completed manually. It provides overloaded `compelte` method to finish operation with some result or error.

`Observable` is interface that describes [observable](https://en.wikipedia.org/wiki/Observer_pattern). It provides interface to subscribe to stream of values and errors that is emitted by observable. 

There are two type of handlers to observer the stream:

* `Consumer` accepts only successful result
* `Observer` accepts successful results and errors and also tracks subscription status

There are following methods to subscribe to stream:

```java
public interface Observable<T> {
  Subscription<T> accept(Consumer<T> consumer);

  Subscription<T> accept(Executor executor, Consumer<T> consumer);

  Subscription<T> observe(Observer<T> observer);
  
  Subscription<T> observe(Executor executor, Observer<T> observer);
}
```

Each method returns `Subscription` to manage the subscription status. Keep in mind that `CompletableFuture` keeps object reference to `Consumer` and `Observer` so subscriber can no be collected by GC until subscription is canceled by `Subscription::cancel` method. 

`CompletableFuture` encapsulates single shot operation or operation that can be submitted multiple times. It depends on constructor parameters. Future returned by `AsyncExecutorService::submit` call is single shot by default.  

`CompletableFuture` provides `map` method that builds a chain of futures to process the stream in desired way. It accepts lambda function or `Transformer` object that process input value and return new value of different or the same type. Errors are also can be processed or rethrown to propagate the exception down the chain.  

## AsyncContextWrapper, CompositeSubscription and CompositeFuture

`CompositeSubscription` combines multiple subscriptions as single entity so they can be managed in handy way. If object is about to be disposed and don't need to receive stream values anymore then all subscription can be canceled at once.

`CompositeFuture` is the same thing but it encapsulates `Future`. It is usable to stop multiple asynchronous operations that are started from current context when object is to be disposed. 

`AsyncContextWrapper` extends standard `ContextWrapper` and provides several methods that delegate its job to `CompositeSubscription` and `CompositeFuture`.

# License
MIT License
Copyright (c) 2019-2020 Pavel Sokolov   