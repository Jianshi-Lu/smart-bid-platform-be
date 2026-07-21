package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.dto.credentialfile.CredentialFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.service.credentialfile.CredentialFileUploadService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 资信文件上传 Controller。
 *
 * <p>上传端点负责接收 multipart 参数，具体批次处理、文件落盘与摘要计算由 Service 编排。
 * 当前阶段不操作 {@code credential_file} 表，删除端点仍为前后端联调占位实现。</p>
 */
@RestController
public class CredentialFileController {

    @Resource
    private CredentialFileUploadService credentialFileUploadService;

    /**
     * 上传一个或多个原始资信文件。
     *
     * @param files         本次上传的文件列表
     * @param bidRecordId   文件所属的投标记录 ID
     * @param uploadBatchId 已存在的上传批次号；第一次上传时不传
     * @return 上传批次号和文件基础信息
     */
    @PostMapping("/credential-files/upload")
    public Result<CredentialFileUploadResponse> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("bidRecordId") Integer bidRecordId,
            @RequestParam(value = "uploadBatchId", required = false) String uploadBatchId) {
        CredentialFileUploadResponse response = credentialFileUploadService.upload(
                files, bidRecordId, uploadBatchId);
        return Result.success(response, "上传成功");
    }

    /**
     * 删除尚未提交投标记录的资信文件。
     *
     * @param uploadBatchId 上传批次号
     * @param fileId        文件记录 ID
     * @return 删除结果
     */
    @DeleteMapping("/credential-files/{uploadBatchId}/files/{fileId}")
    public Result<Void> delete(
            @PathVariable String uploadBatchId,
            @PathVariable Long fileId) {
        System.out.printf("[CREDENTIAL_DELETE] 占位删除成功: batch=%s, fileId=%d%n",
                uploadBatchId, fileId);

        // TODO: 根据 uploadBatchId + fileId 查询 credential_file，校验文件归属。
        // TODO: 删除 original 文件及已生成的 converted Markdown 文件。
        // TODO: 删除或标记 credential_file 记录，已绑定 bid_record 的文件不可按上传阶段删除。
        return Result.success(null, "删除成功（占位）");
    }
}
