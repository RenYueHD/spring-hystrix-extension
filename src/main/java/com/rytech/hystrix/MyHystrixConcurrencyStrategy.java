package com.rytech.hystrix;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.PlatformSpecific;
import com.rytech.threadpool.MyThreadPoolExecutor;
import com.rytech.threadpool.MyThreadPoolTaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用自定义线程池的Hystrix线程隔离策略
 * 有HystrixPlugin使用ServiceLoader(SPI)方式加载
 *
 */
public class MyHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

    Logger logger = LoggerFactory.getLogger(MyHystrixConcurrencyStrategy.class);

    @Override
    public ThreadPoolExecutor getThreadPool(HystrixThreadPoolKey threadPoolKey,
                                            HystrixProperty<Integer> corePoolSize,
                                            HystrixProperty<Integer> maximumPoolSize,
                                            HystrixProperty<Integer> keepAliveTime, TimeUnit unit,
                                            BlockingQueue<Runnable> workQueue) {
        final int dynamicCoreSize = corePoolSize.get();
        final int dynamicMaximumSize = maximumPoolSize.get();

        //若核心线程数 < 最大线程数,且队列支持自定义线程池,使用自定义线程池,此处由于workQueue来自getBlockingQueue(int maxQueueSize),存在被其他delegate重写或被AOP等的可能性,故应做兼容处理
        if (dynamicCoreSize < dynamicMaximumSize && workQueue instanceof MyThreadPoolTaskQueue && ((MyThreadPoolTaskQueue) workQueue).binded()) {
            return new MyThreadPoolExecutor(dynamicCoreSize, dynamicMaximumSize, keepAliveTime.get(), TimeUnit.MINUTES, (MyThreadPoolTaskQueue) workQueue, this.getThreadFactory(threadPoolKey));
        } else {
            logger.warn("Hystrix线程池[{}]配置不适合使用[自定义线程池],将使用默认线程池", threadPoolKey.name());
            return super.getThreadPool(threadPoolKey, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }
    }

    @Override
    public ThreadPoolExecutor getThreadPool(HystrixThreadPoolKey threadPoolKey,
                                            HystrixThreadPoolProperties threadPoolProperties) {
        final boolean allowMaximumSizeToDivergeFromCoreSize = threadPoolProperties.getAllowMaximumSizeToDivergeFromCoreSize().get();
        final int maxQueueSize = threadPoolProperties.maxQueueSize().get();
        final int dynamicMaximumSize = threadPoolProperties.maximumSize().get();
        final int dynamicCoreSize = threadPoolProperties.coreSize().get();

        //若允许线程动态调整大小,且队列 > 0,且最大线程数>核心线程数,则使用优化过的线程池,否则使用默认线程池
        if (allowMaximumSizeToDivergeFromCoreSize && maxQueueSize > 0 && dynamicCoreSize < dynamicMaximumSize) {

            final BlockingQueue<Runnable> workQueue = this.getBlockingQueue(maxQueueSize);

            //此处由于workQueue来自getBlockingQueue(int maxQueueSize),存在被其他delegate重写或被AOP等的可能性,故应做兼容处理
            if (workQueue instanceof MyThreadPoolTaskQueue && !((MyThreadPoolTaskQueue) workQueue).binded()) {
                final MyThreadPoolTaskQueue myThreadPoolTaskQueue = (MyThreadPoolTaskQueue) workQueue;
                final int keepAliveTime = threadPoolProperties.keepAliveTimeMinutes().get();
                final ThreadFactory threadFactory = this.getThreadFactory(threadPoolKey);

                return new MyThreadPoolExecutor(dynamicCoreSize, dynamicMaximumSize, keepAliveTime, TimeUnit.MINUTES, myThreadPoolTaskQueue, threadFactory);
            } else {
                logger.warn("[自定义线程池]队列被重写或已被使用,[自定义线程池]无法生效,将使用Hystrix默认线程池");
                return super.getThreadPool(threadPoolKey, threadPoolProperties);
            }
        } else {
            logger.warn("Hystrix线程池[{}]的配置不适合使用[自定义线程池],将使用默认线程池", threadPoolKey.name());
            return super.getThreadPool(threadPoolKey, threadPoolProperties);
        }
    }

    private static ThreadFactory getThreadFactory(final HystrixThreadPoolKey threadPoolKey) {
        if (!PlatformSpecific.isAppEngineStandardEnvironment()) {
            return new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "my-hystrix-" + threadPoolKey.name() + "-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }

            };
        } else {
            return PlatformSpecific.getAppEngineThreadFactory();
        }
    }

    /**
     * 重写获取队列逻辑
     *
     * @param maxQueueSize
     * @return
     */
    @Override
    public BlockingQueue<Runnable> getBlockingQueue(int maxQueueSize) {
        if (maxQueueSize <= 0) {
            return new SynchronousQueue<>();
        } else {
            return new MyThreadPoolTaskQueue<>(maxQueueSize);
        }
    }

}
