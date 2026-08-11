package io.gitee.lujianshi.bidwinningbackend.config;

import io.gitee.lujianshi.bidwinningbackend.service.FileLifecycleReconciliationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** 应用就绪后异步执行有限次中断恢复；不使用永久轮询，也不阻塞 HTTP 端口启动。 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class FileLifecycleRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(
            FileLifecycleRecoveryRunner.class);

    private final Executor executor;
    private final FileLifecycleReconciliationService reconciliationService;
    private final boolean enabled;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final AtomicBoolean running = new AtomicBoolean();

    public FileLifecycleRecoveryRunner(
            @Qualifier("fileProcessingExecutor") Executor executor,
            FileLifecycleReconciliationService reconciliationService,
            @Value("${file-lifecycle.recovery.enabled:true}") boolean enabled,
            @Value("${file-lifecycle.recovery.max-attempts:5}") int maxAttempts,
            @Value("${file-lifecycle.recovery.retry-delay-ms:2000}") long retryDelayMs) {
        this.executor = executor;
        this.reconciliationService = reconciliationService;
        this.enabled = enabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMs = Math.max(0, Math.min(retryDelayMs, 30_000));
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            trigger(true);
        }
    }

    /** vector-service 就绪回调复用同一对账；已有任务运行时直接合并为该次执行。 */
    public boolean triggerReconciliation() {
        return enabled && trigger(false);
    }

    private boolean trigger(boolean prepareStartup) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        executor.execute(() -> {
            try {
                recoverWithRetries(prepareStartup);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    private void recoverWithRetries(boolean prepareStartup) {
        boolean startupPrepared = !prepareStartup;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (!startupPrepared) {
                    reconciliationService.prepareStartupRecovery();
                    startupPrepared = true;
                }
                FileLifecycleReconciliationService.ReconciliationReport report =
                        reconciliationService.reconcileAll();
                if (report.failures().isEmpty()) {
                    return;
                }
                log.warn(
                        "文件生命周期启动恢复第 {}/{} 次仍有 {} 项失败",
                        attempt,
                        maxAttempts,
                        report.failures().size());
            } catch (RuntimeException failure) {
                log.error(
                        "文件生命周期启动恢复第 {}/{} 次失败",
                        attempt,
                        maxAttempts,
                        failure);
            }
            if (attempt < maxAttempts && !waitBeforeRetry()) {
                return;
            }
        }
        log.error("文件生命周期启动恢复达到最大尝试次数，保留失败状态供下次恢复");
    }

    private boolean waitBeforeRetry() {
        if (retryDelayMs == 0) {
            return true;
        }
        try {
            Thread.sleep(retryDelayMs);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("文件生命周期启动恢复重试等待被中断");
            return false;
        }
    }
}
