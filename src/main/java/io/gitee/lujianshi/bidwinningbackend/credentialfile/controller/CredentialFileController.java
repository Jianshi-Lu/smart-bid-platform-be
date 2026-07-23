package io.gitee.lujianshi.bidwinningbackend.credentialfile.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadRequest;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.UploadService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.PendingUploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatch;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private UploadService uploadService;

    /**
     * 上传一个或多个原始资信文件。
     *
     * @param request multipart 上传请求
     * @return 上传批次号和文件基础信息
     */
    @PostMapping("/credential-files/upload")
    public Result<UploadResponse> upload(
            @Valid @ModelAttribute UploadRequest request) {
        UploadBatch batch = new UploadBatch(
                request.getBidRecordId(),
                toPendingItems(request.getFiles()));
        UploadResponse response = uploadService.upload(batch);
        return Result.success(response, "上传成功");
    }

    /**
     * 将 Spring multipart 文件转换为请求生命周期内的待上传条目。
     *
     * @param files Controller 接收到的 multipart 文件集合
     * @return 后端内部待上传文件集合
     */
    private List<PendingUploadItem> toPendingItems(List<MultipartFile> files) {
        if (files == null) {
            return null;
        }
        return files.stream()
                .map(this::toPendingItem)
                .toList();
    }

    /** 将单个 MultipartFile 适配为不依赖 Spring Web 的内部文件条目。 */
    private PendingUploadItem toPendingItem(MultipartFile file) {
        if (file == null) {
            return null;
        }
        return new PendingUploadItem(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file::getInputStream);
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
