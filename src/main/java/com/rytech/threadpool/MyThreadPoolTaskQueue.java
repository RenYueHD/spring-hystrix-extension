package com.rytech.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 自定义线程池专用队列
 * 此队列使线程池优先创建线程而不是优先将任务入队
 * 由Dubbo的EagerThreadPool对应的TaskQueue改造,增强了兼容性和容错能力
 *
 * @param <T>
 */
public class MyThreadPoolTaskQueue<T extends Runnable> extends LinkedBlockingQueue<Runnable> {

    Logger logger = LoggerFactory.getLogger(MyThreadPoolTaskQueue.class);

    private MyThreadPoolExecutor executor;

    public MyThreadPoolTaskQueue(int capacity) {
        super(capacity);
    }

    public void bind(MyThreadPoolExecutor exec) {
        if (executor == null) {
            this.executor = exec;
        } else {
            //重复绑定Executor大概率为调用方代码编写错误,为避免BUG产生,不应该对同一个Queue重复绑定Executor
            throw new IllegalArgumentException("不允许[MyThreadPoolTaskQueue]重复绑定[MyThreadPoolExecutor]");
        }
    }

    /**
     * 是否已绑定线程池
     *
     * @return
     */
    public boolean binded() {
        return this.executor != null;
    }

    private boolean loged = false;

    /**
     * 将任务入队,入队失败的可能性有2种
     * 第一种是线程未达上限,应该新建线程来执行任务,则入队失败
     * 第二种是线程已达上限,且队列也达上限,则入队失败
     * 为了达到较好的性能,此处允许由于并发创建出略多于实际并发任务数的线程,但仍然不可能超过最大线程,故不选择上锁
     *
     * @param runnable
     * @return
     */
    @Override
    public boolean offer(Runnable runnable) {
        if (executor == null) {
            if (!loged) {
                logger.warn("[MyThreadPoolTaskQueue]无法找到可用线程池,将运行在默认模式");
                loged = true;
            }
            return super.offer(runnable);
        }

        int currentPoolThreadSize = executor.getPoolSize();
        //如果已提交的任务数 < 当前线程池的线程数量,则直接入队让其处理,线程池会自动创建出新线程
        if (executor.getSubmittedTaskCount() < currentPoolThreadSize) {
            return super.offer(runnable);
        }

        //如果已提交的任务数 < 当前线程池的最大线程数,则返回false,此时ThreadPoolExecutor线程池会尝试创建出新的线程来执行任务(关键代码),但由于并发的存在,可能瞬时达到最大线程数导致创建线程失败
        if (currentPoolThreadSize < executor.getMaximumPoolSize()) {
            return false;
        }

        //如果已提交的任务数 >= 当前线程池的最大线程数,则将任务入队,入队可能失败,此时ThreadPoolExecutor线程池会尝试创建出新的线程来执行任务,但必然会失败,并抛出RejectedExecutionException异常
        return super.offer(runnable);
    }

    /**
     * 任务重新尝试入队
     * ThreadPoolExecutor在通过 #offer(Runnable)入队失败后,会抛出RejectedExecutionException异常,此时不一定是队列已满,故应尝试重新入队
     *
     * @param o
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public boolean retryOffer(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
        if (executor.isShutdown()) {
            throw new RejectedExecutionException("线程池已关闭");
        }
        return super.offer(o, timeout, unit);
    }
}
