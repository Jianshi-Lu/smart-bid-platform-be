package io.gitee.lujianshi.bidwinningbackend.knowledgefile.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto.KnowledgeFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileIngestionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 前端知识文件上传、转换和向量化的同步入口。 */
@RestController
@RequestMapping("/knowledge-files")
public class KnowledgeFileController {

    private final KnowledgeFileIngestionService ingestionService;

    public KnowledgeFileController(
            KnowledgeFileIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeFileUploadResponse> upload(
            @RequestPart("files") List<MultipartFile> files) {
        KnowledgeFileUploadResponse response = ingestionService.ingest(
                toUploads(files));
        return Result.success(response, "知识文件已上传并完成向量化");
    }

    private List<KnowledgeFileUpload> toUploads(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw invalidFile("上传知识文件不能为空", null);
        }
        return files.stream()
                .map(file -> {
                    if (file == null) {
                        throw invalidFile("上传知识文件不能为空", null);
                    }
                    try {
                        return new KnowledgeFileUpload(
                                file.getOriginalFilename(),
                                file.getContentType(),
                                file.getSize(),
                                file::getInputStream);
                    } catch (IllegalArgumentException failure) {
                        throw invalidFile("上传知识文件不合法", failure);
                    }
                })
                .toList();
    }

    private AppException invalidFile(String message, Throwable cause) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_FILE_INVALID_FILE,
                "knowledge-file",
                "INGEST",
                "VALIDATE_FILE",
                message,
                cause);
    }
}
