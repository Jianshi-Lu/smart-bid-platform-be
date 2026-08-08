package io.gitee.lujianshi.bidwinningbackend.knowledgefile.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto.KnowledgeFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileIngestionService;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识文件生命周期入口：上传入库、分页追溯、重试与删除。
 *
 * <p>每条记录对应一个 documentUuid，状态在数据库持续留痕；
 * 上传接口逐文件返回成功/失败，失败文件可调用 retry 按失败点续跑。</p>
 */
@RestController
@RequestMapping("/knowledge-files")
public class KnowledgeFileController {

    private final KnowledgeFileIngestionService ingestionService;

    @Resource
    private OperationHistoryService operationHistoryService;

    @Resource
    private FileStatusEventPublisher fileStatusEventPublisher;

    public KnowledgeFileController(
            KnowledgeFileIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** 上传并同步完成 original → Markdown → Chroma；逐文件返回状态。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeFileUploadResponse> upload(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam("username") String username) {
        CurrentAccountContext.CurrentAccount account =
                CurrentAccountContext.get();
        if (account == null
                || username == null
                || !username.equals(account.username())) {
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_INVALID_UPLOAD_REQUEST,
                    "knowledge-file",
                    "INGEST",
                    "VERIFY_USERNAME",
                    "上传账号与当前登录账号不一致");
        }
        KnowledgeFileUploadResponse response = ingestionService.ingest(
                toUploads(files), username);
        return Result.success(response, "知识文件处理完成");
    }

    /** 分页查询知识文件生命周期记录（默认按创建时间倒序）。 */
    @GetMapping
    public Result<PageInfo<KnowledgeFileRecord>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "created_at desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String convertStatus,
            @RequestParam(required = false) String indexStatus) {
        try {
            PageHelper.startPage(page, pageSize, sort);
            List<KnowledgeFileRecord> list = ingestionService.list(
                    keyword, convertStatus, indexStatus);
            return Result.success(new PageInfo<>(list), "查询成功");
        } finally {
            PageHelper.clearPage();
        }
    }

    /** 查询单个知识文件的完整追溯记录（路径、SHA-256、状态时间线、错误）。 */
    @GetMapping("/{documentUuid}")
    public Result<KnowledgeFileRecord> detail(
            @PathVariable String documentUuid) {
        return Result.success(
                ingestionService.getByDocumentUuid(documentUuid), "查询成功");
    }

    /** 查询知识文件的操作历史（上传/转换/向量化/重试/删除，时间倒序）。 */
    @GetMapping("/{documentUuid}/history")
    public Result<List<KnowledgeFileOperationHistory>> listOperationHistory(
            @PathVariable String documentUuid) {
        return Result.success(
                operationHistoryService.listKnowledgeFileHistory(documentUuid),
                "查询成功");
    }

    /** 订阅知识文件转换/向量化状态变化。 */
    @GetMapping(
            value = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter statusEvents() {
        return fileStatusEventPublisher.subscribe(
                FileStatusEventPublisher.KNOWLEDGE_TOPIC);
    }

    /** 按失败点重试：未转换则重转，未向量化则重向量化；返回最新状态。 */
    @PostMapping("/{documentUuid}/retry")
    public Result<KnowledgeFileRecord> retry(
            @PathVariable String documentUuid) {
        return Result.success(
                ingestionService.retry(documentUuid), "重试完成");
    }

    /** 删除知识文件：Chroma 向量 + 对象 + 数据库软删除记录。 */
    @DeleteMapping("/{documentUuid}")
    public Result<Void> delete(@PathVariable String documentUuid) {
        ingestionService.delete(documentUuid);
        return Result.success(null, "删除成功");
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
