package com.rytech.threadpool;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义线程池
 * 当未达到核心线程数时,将优先创建线程,达到最大线程数后才入队
 * 代码来自Dubbo的EagerThreadPool
 */
public class MyThreadPoolExecutor extends ThreadPoolExecutor {

    /**
     * 当前已提交(在队列中或在执行中)的任务总量
     */
    private final AtomicInteger submittedTaskCount = new AtomicInteger(0);

    public MyThreadPoolExecutor(int corePoolSize,
                                int maximumPoolSize,
                                long keepAliveTime,
                                TimeUnit unit,
                                int maxQueueSize,
                                ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new MyThreadPoolTaskQueue<>(maxQueueSize), threadFactory);
    }

    public MyThreadPoolExecutor(int corePoolSize,
                                int maximumPoolSize,
                                long keepAliveTime,
                                TimeUnit unit, MyThreadPoolTaskQueue<Runnable> workQueue,
                                ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new AbortPolicy());
    }

    public MyThreadPoolExecutor(int corePoolSize,
                                int maximumPoolSize,
                                long keepAliveTime,
                                TimeUnit unit,
                                int maxQueueSize,
                                ThreadFactory threadFactory,
                                RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new MyThreadPoolTaskQueue<>(maxQueueSize), threadFactory, handler);
    }

    public MyThreadPoolExecutor(int corePoolSize,
                                int maximumPoolSize,
                                long keepAliveTime,
                                TimeUnit unit, MyThreadPoolTaskQueue<Runnable> workQueue,
                                ThreadFactory threadFactory,
                                RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);

        workQueue.bind(this);
    }

    /**
     * 获取当前已提交(在队列中或在执行中)的任务总量
     *
     * @return
     */
    public int getSubmittedTaskCount() {
        return submittedTaskCount.get();
    }

    /**
     * 在任务执行完成后任务计数器自减
     *
     * @param r
     * @param t
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        submittedTaskCount.decrementAndGet();
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException();
        }
        //任务数自增
        submittedTaskCount.incrementAndGet();
        try {
            //执行任务
            super.execute(command);
        } catch (RejectedExecutionException rx) {
            //当任务被拒绝(可能是队列已满或者无法创建出新线程)时由于不能认定是队列已满,故应尝试重新入队
            final MyThreadPoolTaskQueue queue = (MyThreadPoolTaskQueue) super.getQueue();
            try {
                if (!queue.retryOffer(command, 0, TimeUnit.MILLISECONDS)) {
                    submittedTaskCount.decrementAndGet();
                    throw new RejectedExecutionException("线程池任务队列已满,任务无法执行", rx);
                }
            } catch (InterruptedException x) {
                submittedTaskCount.decrementAndGet();
                throw new RejectedExecutionException(x);
            }
        } catch (Throwable t) {
            submittedTaskCount.decrementAndGet();
            throw t;
        }
    }
}
