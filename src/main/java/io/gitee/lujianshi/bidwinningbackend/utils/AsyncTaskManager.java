package io.gitee.lujianshi.bidwinningbackend.utils;

/**
 * 异步任务管理器（轻量级方案）
 *
 * 工具类特点：
 * - 非 Spring Bean，纯静态方法
 * - 直接调用 AsyncTaskManager.execute(task, delayMillis) 即可
 * - 每个任务创建一个新线程，不阻塞调用方
 *
 * 设计说明：
 * - Runnable task：业务逻辑（任务），只负责定义"做什么"
 * - long delayMillis：延迟时间（毫秒），控制"什么时候做"
 * - new Thread(...).start()：创建并启动新线程，立即返回，不阻塞主线程
 *
 * 生产环境建议：
 * - 当前方案每次调用创建新线程，高频调用时有线程创建开销
 * - 可改用线程池（ExecutorService）复用线程，减少开销
 */
public class AsyncTaskManager {

    /**
     * 执行异步任务（延迟后执行，不阻塞调用方）
     *
     * 参数说明：
     * @param task        Runnable 接口实现类实例，定义具体业务逻辑
     *                   调用时传入 Lambda，编译器自动转换为匿名 Runnable 实现
     *                   例如：() -> System.out.println("done")
     *                   等价于：new Runnable() { @Override run() { System.out.println("done"); } }
     * @param delayMillis 延迟时间（毫秒），Thread.sleep() 阻塞新线程，不阻塞调用方主线程
     *
     * 执行流程：
     * 1. new Thread(() -> { ... })：创建新线程
     *    - () -> {} 是 Lambda 表达式，简化 new Runnable() { @Override run() {...} }
     *    - Runnable 是接口，仅含 run() 方法，Lambda 自动填充方法体
     * 2. Thread.sleep(delayMillis)：让新线程"睡"指定时间
     *    - 阻塞的是 new 出来的子线程，不是调用 execute 的主线程
     *    - 实现"延迟执行"效果，同时主线程继续运行不阻塞
     * 3. task.run()：睡醒后执行传入的业务逻辑
     * 4. .start()：启动新线程（start 后立即返回，不等待线程执行完成）
     *
     * 异常处理：
     * - InterruptedException：捕获 sleep 过程中的中断信号
     * - Thread.currentThread().interrupt()：恢复线程中断状态，符合 Java 规范
     * - throw new RuntimeException(e)：转换为未检查异常上抛
     *
     * @param task        要执行的任务（Runnable 接口）
     * @param delayMillis 延迟多少毫秒后再执行
     */
    public static void execute(Runnable task, long delayMillis) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMillis);  // 阻塞子线程，实现延迟，不阻塞主线程
                task.run();                   // 执行传入的业务逻辑
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // 恢复中断状态
                throw new RuntimeException(e);       // 转换为未检查异常
            }
        }).start();  // 启动新线程，立即返回，不等待执行完成
    }
}