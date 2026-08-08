package io.gitee.lujianshi.bidwinningbackend.credentialfile.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资信文件操作历史查询入口。
 *
 * <p>只读接口，受 {@code WebMvcConfig} 中 {@code /credential-files/**}
 * 的招标方鉴权保护；历史由上传/转换/删除服务在对象存储变更时同步写入。</p>
 *
 * @since V40
 */
@RestController
@RequestMapping("/credential-files")
public class CredentialFileOperationHistoryController {

    @Resource
    private OperationHistoryService operationHistoryService;

    /** 查询资信文件的操作历史（上传/转换/删除，时间倒序）。 */
    @GetMapping("/{fileId}/history")
    public Result<List<CredentialFileOperationHistory>> listOperationHistory(
            @PathVariable Long fileId) {
        return Result.success(
                operationHistoryService.listCredentialFileHistory(fileId),
                "查询成功");
    }
}
