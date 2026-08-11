package io.gitee.lujianshi.bidwinningbackend.service.file;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileConvertService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto.KnowledgeFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileIngestionService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.service.file.FileDownloadService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 招标方知识文件和投标资信文件的统一应用层编排。 */
@Service
public class BidInviterFileApplicationService {

    private final KnowledgeFileIngestionService ingestionService;
    private final FileConvertService conversionService;
    private final FileUploadService uploadService;
    private final OperationHistoryService operationHistoryService;
    private final FileStatusEventPublisher fileStatusEventPublisher;
    private final FileDownloadService fileDownloadService;

    public BidInviterFileApplicationService(
            KnowledgeFileIngestionService ingestionService,
            FileConvertService conversionService,
            FileUploadService uploadService,
            OperationHistoryService operationHistoryService,
            FileStatusEventPublisher fileStatusEventPublisher,
            FileDownloadService fileDownloadService) {
        this.ingestionService = ingestionService;
        this.conversionService = conversionService;
        this.uploadService = uploadService;
        this.operationHistoryService = operationHistoryService;
        this.fileStatusEventPublisher = fileStatusEventPublisher;
        this.fileDownloadService = fileDownloadService;
    }

    /** 校验拦截器写入的招标方账号后，批量进入知识文件生命周期。 */
    public KnowledgeFileUploadResponse uploadKnowledgeFiles(
            List<KnowledgeFileUpload> files,
            String username) {
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
        return ingestionService.ingest(files, username);
    }

    /** 在应用层封装 PageHelper 的线程绑定和清理边界。 */
    public PageInfo<KnowledgeFileRecord> listKnowledgeFiles(
            int page,
            int pageSize,
            String sort,
            String keyword,
            String convertStatus,
            String indexStatus) {
        try {
            PageHelper.startPage(page, pageSize, sort);
            return new PageInfo<>(ingestionService.list(
                    keyword, convertStatus, indexStatus));
        } finally {
            PageHelper.clearPage();
        }
    }

    public KnowledgeFileRecord getKnowledgeFile(String documentUuid) {
        return ingestionService.getByDocumentUuid(documentUuid);
    }

    public List<KnowledgeFileOperationHistory> listKnowledgeFileHistory(
            String documentUuid) {
        return operationHistoryService.listKnowledgeFileHistory(documentUuid);
    }

    public SseEmitter subscribeKnowledgeFileEvents() {
        return fileStatusEventPublisher.subscribe(
                FileStatusEventPublisher.KNOWLEDGE_TOPIC);
    }

    public KnowledgeFileRecord retryKnowledgeFile(String documentUuid) {
        return ingestionService.retry(documentUuid);
    }

    public void deleteKnowledgeFile(String documentUuid) {
        ingestionService.delete(documentUuid);
    }

    public FileConvertResponse convertCredentialFiles(Integer bidRecordId) {
        return conversionService.convertAll(bidRecordId);
    }

    /** 招标方从已被 Agent 引用的投标记录读取其未删除资信材料。 */
    public List<CredentialFile> listCredentialFiles(Integer bidRecordId) {
        return uploadService.listByBidRecordId(bidRecordId);
    }

    public List<CredentialFileOperationHistory> listCredentialFileHistory(
            Long fileId) {
        return operationHistoryService.listCredentialFileHistory(fileId);
    }

    public FileDownloadService.DownloadResource openCredentialOriginal(
            Long fileId) {
        return fileDownloadService.openCredentialOriginal(fileId, null);
    }

    public FileDownloadService.DownloadResource openCredentialMarkdown(
            Long fileId) {
        return fileDownloadService.openCredentialMarkdown(fileId, null);
    }

    public FileDownloadService.DownloadResource openKnowledgeOriginal(
            String documentUuid) {
        return fileDownloadService.openKnowledgeOriginal(documentUuid);
    }

    public FileDownloadService.DownloadResource openKnowledgeMarkdown(
            String documentUuid) {
        return fileDownloadService.openKnowledgeMarkdown(documentUuid);
    }
}
