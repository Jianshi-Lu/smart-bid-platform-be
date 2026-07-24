package io.gitee.lujianshi.bidwinningbackend.credentialfile.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadRequest;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
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

/** 原始资信文件上传 Controller。 */
@RestController
public class FileUploadController {

    @Resource
    private FileUploadService uploadService;

    /** 上传一个或多个原始资信文件。 */
    @PostMapping("/credential-files/upload")
    public Result<UploadResponse> upload(
            @Valid @ModelAttribute UploadRequest request) {
        UploadBatch batch = new UploadBatch(    // 将http请求转换为业务类型
                request.getBidRecordId(),
                toUploadItems(request.getFiles()));
        UploadResponse response = uploadService.upload(batch);
        return Result.success(response, "上传成功");
    }

    /** 将 Spring multipart 文件转换为请求生命周期内的待上传条目。 */
    private List<UploadItem> toUploadItems(List<MultipartFile> files) {
        if (files == null) {
            return null;
        }
        return files.stream()
                .map(file -> file == null
                        ? null
                        : new UploadItem(
                                file.getOriginalFilename(), // 第 1 个参数 → originalFilename
                                file.getContentType(),      // 第 2 个参数 → contentType
                                file.getSize(),             // 第 3 个参数 → fileSize
                                file::getInputStream))      // 第 4 个参数 → content
                .toList();
    }

    /** 删除尚未提交投标记录的资信文件。 */
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
