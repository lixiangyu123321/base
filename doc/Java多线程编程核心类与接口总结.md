# Java 多线程编程核心类与接口总结

## 文档信息

- **创建日期**: 2025-01-27
- **Java 版本**: JDK 8+
- **主题**: 多线程编程核心知识

---

## 一、核心类与接口概览

### 1.1 类继承关系图

```
Object
├── Thread (类)
│   └── 实现 Runnable
│
├── Runnable (接口)
│   └── 被 Thread、Executor 等实现
│
├── Callable<V> (接口)
│   └── 类似 Runnable，但返回结果
│
├── Future<V> (接口)
│   ├── RunnableFuture<V> (接口)
│   │   └── FutureTask<V> (类)
│   └── CompletableFuture<V> (类)
│
├── Executor (接口)
│   ├── ExecutorService (接口)
│   │   ├── AbstractExecutorService (抽象类)
│   │   │   └── ThreadPoolExecutor (类)
│   │   │       └── ScheduledThreadPoolExecutor (类)
│   │   └── ScheduledExecutorService (接口)
│   │       └── 被 ScheduledThreadPoolExecutor 实现
│   └── ForkJoinPool (类)
│
├── CountDownLatch (类)
│   └── 继承自 AbstractQueuedSynchronizer
│
└── Semaphore (类)
    └── 继承自 AbstractQueuedSynchronizer
```

### 1.2 核心类分类

| 类别 | 类/接口 | 说明 |
|------|---------|------|
| **基础线程** | `Thread` | 线程类 |
| | `Runnable` | 任务接口 |
| | `Callable<V>` | 带返回值的任务接口 |
| **异步结果** | `Future<V>` | 异步结果接口 |
| | `FutureTask<V>` | Future 实现类 |
| | `CompletableFuture<V>` | 增强的 Future |
| **线程池** | `Executor` | 执行器接口 |
| | `ExecutorService` | 执行器服务接口 |
| | `ThreadPoolExecutor` | 线程池实现 |
| | `ScheduledThreadPoolExecutor` | 定时线程池 |
| | `ForkJoinPool` | 分治线程池 |
| **同步工具** | `CountDownLatch` | 倒计时门闩 |
| | `Semaphore` | 信号量 |
| | `CyclicBarrier` | 循环屏障 |
| | `Phaser` | 阶段器 |

---

## 二、基础线程类

### 2.1 Thread（线程类）

**职责**：表示一个执行线程

**继承关系**：
```java
class Thread implements Runnable
```

**核心方法**：
- `start()` - 启动线程
- `run()` - 线程执行体
- `sleep(long millis)` - 休眠
- `join()` - 等待线程结束
- `interrupt()` - 中断线程
- `isInterrupted()` - 检查中断状态

**使用示例**：
```java
// 方式1：继承 Thread
class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("线程执行");
    }
}
MyThread thread = new MyThread();
thread.start();

// 方式2：实现 Runnable
Thread thread2 = new Thread(() -> {
    System.out.println("线程执行");
});
thread2.start();
```

**特点**：
- ✅ 直接创建线程
- ❌ 不能返回值
- ❌ 不能抛出受检异常
- ❌ 资源消耗大（不推荐直接使用）

### 2.2 Runnable（任务接口）

**职责**：定义可执行的任务

**接口定义**：
```java
@FunctionalInterface
public interface Runnable {
    void run();
}
```

**实现关系**：
- `Thread` 实现 `Runnable`
- 被 `Executor` 使用

**使用示例**：
```java
// Lambda 表达式
Runnable task = () -> {
    System.out.println("执行任务");
};

// 提交到线程池
executor.execute(task);
```

**特点**：
- ✅ 函数式接口（@FunctionalInterface）
- ✅ 无返回值
- ✅ 不能抛出受检异常

### 2.3 Callable<V>（带返回值的任务接口）

**职责**：定义可执行并返回结果的任务

**接口定义**：
```java
@FunctionalInterface
public interface Callable<V> {
    V call() throws Exception;
}
```

**与 Runnable 的区别**：
| 特性 | Runnable | Callable |
|------|----------|----------|
| 返回值 | void | V |
| 异常 | 不能抛出受检异常 | 可以抛出异常 |
| 使用场景 | 简单任务 | 需要返回值的任务 |

**使用示例**：
```java
Callable<String> task = () -> {
    Thread.sleep(1000);
    return "任务完成";
};

Future<String> future = executor.submit(task);
String result = future.get();
```

---

## 三、异步结果类

### 3.1 Future<V>（异步结果接口）

**职责**：表示异步计算的结果

**接口定义**：
```java
public interface Future<V> {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isCancelled();
    boolean isDone();
    V get() throws InterruptedException, ExecutionException;
    V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}
```

**核心方法**：
- `get()` - 阻塞获取结果
- `get(timeout, unit)` - 超时获取结果
- `cancel()` - 取消任务
- `isDone()` - 检查是否完成
- `isCancelled()` - 检查是否取消

**使用示例**：
```java
ExecutorService executor = Executors.newFixedThreadPool(5);
Future<String> future = executor.submit(() -> {
    Thread.sleep(2000);
    return "结果";
});

// 阻塞等待结果
String result = future.get();

// 超时等待
try {
    String result = future.get(1, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    future.cancel(true);
}
```

**特点**：
- ✅ 异步获取结果
- ✅ 支持取消
- ❌ 不支持链式调用
- ❌ 不支持组合多个 Future

### 3.2 FutureTask<V>（Future 实现类）

**职责**：Future 和 Runnable 的实现，可被提交到线程池

**继承关系**：
```java
class FutureTask<V> implements RunnableFuture<V>
interface RunnableFuture<V> extends Runnable, Future<V>
```

**使用示例**：
```java
Callable<String> callable = () -> "结果";
FutureTask<String> futureTask = new FutureTask<>(callable);

// 方式1：提交到线程池
executor.submit(futureTask);

// 方式2：直接运行
Thread thread = new Thread(futureTask);
thread.start();

String result = futureTask.get();
```

**特点**：
- ✅ 既是 Runnable 又是 Future
- ✅ 可被提交到线程池或直接运行
- ✅ 结果可被多次获取

### 3.3 CompletableFuture<V>（增强的 Future）

**职责**：提供更强大的异步编程能力

**继承关系**：
```java
class CompletableFuture<V> implements Future<V>, CompletionStage<V>
```

**核心功能**：
1. **链式调用**：支持 thenApply、thenCompose 等
2. **组合操作**：支持 allOf、anyOf 等
3. **异常处理**：支持 exceptionally、handle 等
4. **异步执行**：支持 supplyAsync、runAsync 等

**使用示例**：
```java
// 1. 创建异步任务
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return "Hello";
});

// 2. 链式调用
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> "Hello")
    .thenApply(s -> s + " World")
    .thenApply(String::toUpperCase);

// 3. 组合多个 Future
CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> "Task1");
CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> "Task2");
CompletableFuture<String> combined = future1.thenCombine(future2, (a, b) -> a + b);

// 4. 等待所有完成
CompletableFuture.allOf(future1, future2).join();

// 5. 异常处理
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> {
        if (true) throw new RuntimeException("错误");
        return "成功";
    })
    .exceptionally(ex -> "失败: " + ex.getMessage());
```

**常用方法**：

| 方法 | 说明 |
|------|------|
| `supplyAsync()` | 异步执行有返回值的任务 |
| `runAsync()` | 异步执行无返回值的任务 |
| `thenApply()` | 转换结果 |
| `thenCompose()` | 组合另一个 CompletableFuture |
| `thenCombine()` | 合并两个 Future 的结果 |
| `thenAccept()` | 消费结果 |
| `exceptionally()` | 异常处理 |
| `handle()` | 处理结果或异常 |
| `allOf()` | 等待所有完成 |
| `anyOf()` | 等待任意一个完成 |

**特点**：
- ✅ 强大的链式调用
- ✅ 支持组合多个 Future
- ✅ 完善的异常处理
- ✅ 支持自定义线程池

---

## 四、线程池类

### 4.1 Executor（执行器接口）

**职责**：定义执行任务的接口

**接口定义**：
```java
public interface Executor {
    void execute(Runnable command);
}
```

**特点**：
- ✅ 解耦任务提交和执行
- ✅ 最简单的执行器接口

### 4.2 ExecutorService（执行器服务接口）

**职责**：扩展 Executor，提供更丰富的功能

**继承关系**：
```java
interface ExecutorService extends Executor
```

**核心方法**：
- `submit()` - 提交任务，返回 Future
- `invokeAll()` - 提交多个任务
- `invokeAny()` - 提交多个任务，返回第一个完成的结果
- `shutdown()` - 优雅关闭
- `shutdownNow()` - 立即关闭
- `awaitTermination()` - 等待关闭完成

**使用示例**：
```java
ExecutorService executor = Executors.newFixedThreadPool(5);

// 提交任务
Future<String> future = executor.submit(() -> "结果");

// 提交多个任务
List<Callable<String>> tasks = Arrays.asList(
    () -> "Task1",
    () -> "Task2",
    () -> "Task3"
);
List<Future<String>> futures = executor.invokeAll(tasks);

// 关闭线程池
executor.shutdown();
executor.awaitTermination(10, TimeUnit.SECONDS);
```

### 4.3 ThreadPoolExecutor（线程池实现类）

**职责**：线程池的核心实现

**继承关系**：
```java
class ThreadPoolExecutor extends AbstractExecutorService
abstract class AbstractExecutorService implements ExecutorService
```

**核心参数**：
```java
ThreadPoolExecutor(
    int corePoolSize,              // 核心线程数
    int maximumPoolSize,           // 最大线程数
    long keepAliveTime,            // 空闲线程存活时间
    TimeUnit unit,                 // 时间单位
    BlockingQueue<Runnable> workQueue,  // 工作队列
    ThreadFactory threadFactory,        // 线程工厂
    RejectedExecutionHandler handler    // 拒绝策略
)
```

**线程池工作流程**：
```
1. 提交任务
2. 如果核心线程数未满，创建新线程执行
3. 如果核心线程数已满，任务进入队列
4. 如果队列已满，创建新线程（不超过最大线程数）
5. 如果达到最大线程数，执行拒绝策略
```

**使用示例**：
```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,                              // 核心线程数
    10,                             // 最大线程数
    60L,                            // 空闲线程存活时间
    TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100), // 工作队列
    new ThreadFactory() {           // 线程工厂
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("MyThread-" + t.getId());
            return t;
        }
    },
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
);
```

**拒绝策略**：
| 策略 | 说明 |
|------|------|
| `AbortPolicy` | 抛出异常（默认） |
| `CallerRunsPolicy` | 调用者线程执行 |
| `DiscardPolicy` | 直接丢弃 |
| `DiscardOldestPolicy` | 丢弃最老的任务 |

### 4.4 ScheduledThreadPoolExecutor（定时线程池）

**职责**：支持定时和周期性任务的线程池

**继承关系**：
```java
class ScheduledThreadPoolExecutor extends ThreadPoolExecutor 
    implements ScheduledExecutorService
```

**核心方法**：
- `schedule()` - 延迟执行
- `scheduleAtFixedRate()` - 固定频率执行
- `scheduleWithFixedDelay()` - 固定延迟执行

**使用示例**：
```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

// 延迟执行
scheduler.schedule(() -> {
    System.out.println("延迟执行");
}, 5, TimeUnit.SECONDS);

// 固定频率执行（每5秒执行一次）
scheduler.scheduleAtFixedRate(() -> {
    System.out.println("定时执行");
}, 0, 5, TimeUnit.SECONDS);

// 固定延迟执行（执行完成后延迟5秒再执行）
scheduler.scheduleWithFixedDelay(() -> {
    System.out.println("延迟执行");
}, 0, 5, TimeUnit.SECONDS);
```

**特点**：
- ✅ 支持定时任务
- ✅ 支持周期性任务
- ✅ 适合任务调度场景

### 4.5 ForkJoinPool（分治线程池）

**职责**：用于分治算法和并行计算的线程池

**特点**：
- ✅ 工作窃取算法
- ✅ 适合 CPU 密集型任务
- ✅ 自动负载均衡

**使用示例**：
```java
ForkJoinPool pool = new ForkJoinPool();

// 提交 ForkJoinTask
ForkJoinTask<Integer> task = pool.submit(() -> {
    // 分治任务
    return 100;
});

Integer result = task.join();
```

---

## 五、Executors（线程池工厂类）

**职责**：提供创建线程池的便捷方法

**常用方法**：

| 方法 | 说明 | 返回类型 |
|------|------|---------|
| `newFixedThreadPool(n)` | 固定大小线程池 | ExecutorService |
| `newCachedThreadPool()` | 缓存线程池 | ExecutorService |
| `newSingleThreadExecutor()` | 单线程池 | ExecutorService |
| `newScheduledThreadPool(n)` | 定时线程池 | ScheduledExecutorService |
| `newWorkStealingPool()` | 工作窃取线程池 | ExecutorService |

**使用示例**：
```java
// 固定大小线程池
ExecutorService executor = Executors.newFixedThreadPool(5);

// 缓存线程池（自动扩容）
ExecutorService executor2 = Executors.newCachedThreadPool();

// 单线程池（保证顺序执行）
ExecutorService executor3 = Executors.newSingleThreadExecutor();

// 定时线程池
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
```

**注意**：
- ⚠️ `newFixedThreadPool` 使用无界队列，可能导致 OOM
- ⚠️ `newCachedThreadPool` 线程数无上限，可能导致资源耗尽
- ✅ 推荐使用 `ThreadPoolExecutor` 自定义参数

---

## 六、同步工具类

### 6.1 CountDownLatch（倒计时门闩）

**职责**：等待一个或多个线程完成

**继承关系**：
```java
class CountDownLatch
    // 内部使用 AbstractQueuedSynchronizer
```

**核心方法**：
- `CountDownLatch(int count)` - 构造函数，指定计数
- `countDown()` - 计数减1
- `await()` - 等待计数为0
- `await(timeout, unit)` - 超时等待

**使用示例**：
```java
CountDownLatch latch = new CountDownLatch(3);

// 启动3个线程
for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        try {
            // 执行任务
            Thread.sleep(1000);
        } finally {
            latch.countDown();  // 计数减1
        }
    }).start();
}

latch.await();  // 等待所有线程完成
System.out.println("所有任务完成");
```

**应用场景**：
- ✅ 等待多个线程完成
- ✅ 主线程等待子线程
- ✅ 并行任务同步

**特点**：
- ✅ 一次性使用（计数不能重置）
- ✅ 适合等待多个任务完成

### 6.2 CyclicBarrier（循环屏障）

**职责**：让一组线程到达屏障时被阻塞，直到最后一个线程到达

**使用示例**：
```java
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    System.out.println("所有线程到达屏障");
});

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        try {
            // 执行任务
            barrier.await();  // 等待其他线程
        } catch (Exception e) {
            e.printStackTrace();
        }
    }).start();
}
```

**与 CountDownLatch 的区别**：
| 特性 | CountDownLatch | CyclicBarrier |
|------|----------------|---------------|
| 计数 | 递减 | 递增 |
| 重用 | 不可重用 | 可重用 |
| 用途 | 等待多个任务完成 | 等待多个线程到达 |

### 6.3 Semaphore（信号量）

**职责**：控制同时访问资源的线程数

**使用示例**：
```java
Semaphore semaphore = new Semaphore(3);  // 允许3个线程同时访问

for (int i = 0; i < 10; i++) {
    new Thread(() -> {
        try {
            semaphore.acquire();  // 获取许可
            // 访问资源
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();  // 释放许可
        }
    }).start();
}
```

**应用场景**：
- ✅ 限流
- ✅ 资源池管理
- ✅ 控制并发数

---

## 七、完整示例

### 7.1 线程池 + Future

```java
ExecutorService executor = Executors.newFixedThreadPool(5);

List<Future<String>> futures = new ArrayList<>();
for (int i = 0; i < 10; i++) {
    final int index = i;
    Future<String> future = executor.submit(() -> {
        Thread.sleep(1000);
        return "Task " + index + " completed";
    });
    futures.add(future);
}

// 获取结果
for (Future<String> future : futures) {
    try {
        String result = future.get();
        System.out.println(result);
    } catch (Exception e) {
        e.printStackTrace();
    }
}

executor.shutdown();
```

### 7.2 CompletableFuture 链式调用

```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> {
        System.out.println("任务1");
        return "Hello";
    })
    .thenApply(s -> {
        System.out.println("任务2");
        return s + " World";
    })
    .thenApply(String::toUpperCase)
    .thenCompose(s -> CompletableFuture.supplyAsync(() -> {
        System.out.println("任务3");
        return s + "!";
    }))
    .exceptionally(ex -> {
        System.out.println("异常: " + ex.getMessage());
        return "Error";
    });

String result = future.join();
System.out.println(result);
```

### 7.3 CountDownLatch 同步

```java
int threadCount = 5;
CountDownLatch latch = new CountDownLatch(threadCount);
ExecutorService executor = Executors.newFixedThreadPool(threadCount);

for (int i = 0; i < threadCount; i++) {
    executor.submit(() -> {
        try {
            // 执行任务
            Thread.sleep(1000);
            System.out.println("任务完成");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            latch.countDown();
        }
    });
}

latch.await();
System.out.println("所有任务完成");
executor.shutdown();
```

### 7.4 定时任务调度

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

// 延迟执行
scheduler.schedule(() -> {
    System.out.println("延迟执行");
}, 5, TimeUnit.SECONDS);

// 固定频率执行
scheduler.scheduleAtFixedRate(() -> {
    System.out.println("定时执行");
}, 0, 5, TimeUnit.SECONDS);

// 固定延迟执行
scheduler.scheduleWithFixedDelay(() -> {
    System.out.println("延迟执行");
}, 0, 5, TimeUnit.SECONDS);
```

---

## 八、最佳实践

### 8.1 线程池选择

| 场景 | 推荐线程池 |
|------|-----------|
| CPU 密集型 | `newFixedThreadPool(CPU核心数+1)` |
| IO 密集型 | `newCachedThreadPool()` 或自定义大线程池 |
| 定时任务 | `newScheduledThreadPool()` |
| 分治算法 | `ForkJoinPool` |

### 8.2 线程池参数设置

```java
// CPU 核心数
int corePoolSize = Runtime.getRuntime().availableProcessors();

// IO 密集型：线程数 = CPU核心数 * (1 + IO等待时间/CPU计算时间)
int ioThreads = corePoolSize * 2;

ThreadPoolExecutor executor = new ThreadPoolExecutor(
    corePoolSize,           // 核心线程数
    ioThreads,              // 最大线程数
    60L,                    // 空闲线程存活时间
    TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(1000),  // 有界队列
    new ThreadFactory() {
        private AtomicInteger count = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("Worker-" + count.incrementAndGet());
            t.setDaemon(false);
            return t;
        }
    },
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
);
```

### 8.3 异常处理

```java
// Future 异常处理
Future<String> future = executor.submit(() -> {
    throw new RuntimeException("错误");
});

try {
    String result = future.get();
} catch (ExecutionException e) {
    Throwable cause = e.getCause();
    // 处理异常
}

// CompletableFuture 异常处理
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> {
        throw new RuntimeException("错误");
    })
    .exceptionally(ex -> {
        // 异常处理
        return "默认值";
    });
```

### 8.4 资源管理

```java
ExecutorService executor = Executors.newFixedThreadPool(5);

try {
    // 使用线程池
    executor.submit(() -> {
        // 任务
    });
} finally {
    // 优雅关闭
    executor.shutdown();
    try {
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

---

## 九、类图总结

### 9.1 核心继承关系

```
Executor (接口)
  └── ExecutorService (接口)
      └── AbstractExecutorService (抽象类)
          └── ThreadPoolExecutor (类)
              └── ScheduledThreadPoolExecutor (类)

Runnable (接口)
  └── 被 Thread、Executor 使用

Callable<V> (接口)
  └── 被 ExecutorService.submit() 使用

Future<V> (接口)
  ├── RunnableFuture<V> (接口)
  │   └── FutureTask<V> (类)
  └── CompletableFuture<V> (类)
      └── 实现 CompletionStage<V>
```

### 9.2 职责划分

| 类/接口 | 职责 |
|---------|------|
| `Thread` | 线程表示和执行 |
| `Runnable` | 任务定义（无返回值） |
| `Callable<V>` | 任务定义（有返回值） |
| `Future<V>` | 异步结果获取 |
| `CompletableFuture<V>` | 增强的异步编程 |
| `Executor` | 任务执行接口 |
| `ExecutorService` | 扩展的执行器服务 |
| `ThreadPoolExecutor` | 线程池实现 |
| `ScheduledThreadPoolExecutor` | 定时线程池 |
| `CountDownLatch` | 线程同步（等待完成） |
| `CyclicBarrier` | 线程同步（等待到达） |
| `Semaphore` | 资源访问控制 |

---

## 十、总结

### 10.1 核心要点

1. **基础线程**：`Thread`、`Runnable`、`Callable`
2. **异步结果**：`Future`、`FutureTask`、`CompletableFuture`
3. **线程池**：`ExecutorService`、`ThreadPoolExecutor`、`ScheduledThreadPoolExecutor`
4. **同步工具**：`CountDownLatch`、`CyclicBarrier`、`Semaphore`

### 10.2 选择建议

- **简单任务**：使用 `Runnable` + 线程池
- **需要返回值**：使用 `Callable` + `Future`
- **复杂异步**：使用 `CompletableFuture`
- **定时任务**：使用 `ScheduledThreadPoolExecutor`
- **线程同步**：使用 `CountDownLatch`、`CyclicBarrier`
- **资源控制**：使用 `Semaphore`

### 10.3 注意事项

- ⚠️ 避免直接使用 `Thread`，优先使用线程池
- ⚠️ 线程池使用完毕要关闭
- ⚠️ 注意异常处理
- ⚠️ 合理设置线程池参数
- ⚠️ 避免死锁和资源竞争

---

**本文档涵盖了 Java 多线程编程的核心类和接口，可作为参考手册使用。**

