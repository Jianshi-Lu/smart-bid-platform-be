package io.gitee.lujianshi.bidwinningbackend.utils;

/**
 * 异步任务管理器。纯 Java 线程（非 Spring Bean），每次 {@code new Thread().start()}。
 *
 * <p>高频调用建议改用线程池复用。</p>
 */
public class AsyncTaskManager {

    /**
     * 延迟执行任务，不阻塞调用方。
     *
     * @param task        业务逻辑
     * @param delayMillis 延迟毫秒数（阻塞子线程，不阻塞主线程）
     */
    public static void execute(Runnable task, long delayMillis) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                System.out.println("异步任务执行完成: delayMillis=" + delayMillis);
            }
        }).start();
    }
}
