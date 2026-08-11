package io.gitee.lujianshi.bidwinningbackend.controller.internal;

import io.gitee.lujianshi.bidwinningbackend.config.FileLifecycleRecoveryRunner;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** vector-service 本机进程就绪后触发文件与向量幂等对账；不承载任何业务数据。 */
@RestController
@RequestMapping("/internal/file-lifecycle")
public class FileLifecycleInternalController {

    private final FileLifecycleRecoveryRunner recoveryRunner;

    public FileLifecycleInternalController(FileLifecycleRecoveryRunner recoveryRunner) {
        this.recoveryRunner = recoveryRunner;
    }

    @PostMapping("/reconcile")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TriggerResponse reconcile(HttpServletRequest request) {
        requireLoopback(request == null ? null : request.getRemoteAddr());
        return new TriggerResponse(recoveryRunner.triggerReconciliation());
    }

    private void requireLoopback(String remoteAddress) {
        try {
            if (remoteAddress == null
                    || !InetAddress.getByName(remoteAddress).isLoopbackAddress()) {
                throw forbidden();
            }
        } catch (UnknownHostException failure) {
            throw forbidden();
        }
    }

    private AppException forbidden() {
        return new AppException(
                AppErrorCode.BID_RECORD_FORBIDDEN,
                "file-lifecycle",
                "RECONCILE",
                "CHECK_LOOPBACK",
                "文件生命周期内部对账仅允许本机服务触发");
    }

    public record TriggerResponse(boolean accepted) {
    }
}
