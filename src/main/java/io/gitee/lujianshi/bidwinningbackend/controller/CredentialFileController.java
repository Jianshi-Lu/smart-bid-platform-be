package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 资信文件上传 Controller。
 *
 * <p>当前仅提供前后端联调占位端点：不写本地文件、不操作 {@code credential_file} 表，
 * 只生成或复用上传批次号并返回基础文件信息。</p>
 */
@RestController
public class CredentialFileController {

    /**
     * 上传一个或多个原始资信文件。
     *
     * @param files         本次上传的文件列表
     * @param uploadBatchId 已存在的上传批次号；第一次上传时不传
     * @return 上传批次号和文件基础信息
     */
    @PostMapping("/credential-files/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "uploadBatchId", required = false) String uploadBatchId) {
        // 校验前端输入
        if (files == null || files.isEmpty()) {
            return Result.error(400, "上传文件不能为空");
        }

        // 生成uuid
        String effectiveBatchId = uploadBatchId == null || uploadBatchId.isBlank()
                ? UUID.randomUUID().toString()
                : uploadBatchId;

        List<Map<String, Object>> fileItems = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileOrder", index);
            item.put("originalFilename", file.getOriginalFilename());
            item.put("contentType", file.getContentType());
            item.put("fileSize", file.getSize());
            item.put("convertStatus", "UPLOADED");
            fileItems.add(item);
        }

        System.out.printf("[CREDENTIAL_UPLOAD] 占位上传成功: batch=%s, count=%d%n",
                effectiveBatchId, files.size());

        // TODO: 校验已有 uploadBatchId 是否存在，并校验当前请求是否有权操作该批次。
        // TODO: 生成批次目录，在 original 目录保存原始文件并计算 SHA-256。
        // TODO: 保存成功后插入 credential_file，bid_record_id 保持 NULL。
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uploadBatchId", effectiveBatchId);
        data.put("files", fileItems);
        return Result.success(data, "上传成功（占位）");
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
