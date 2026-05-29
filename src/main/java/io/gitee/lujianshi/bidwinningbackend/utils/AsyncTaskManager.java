package io.gitee.lujianshi.bidwinningbackend.utils;

public class AsyncTaskManager {
    public static void execute(Runnable task, long delayMillis) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }).start();
    }
}
