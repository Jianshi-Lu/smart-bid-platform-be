package io.gitee.lujianshi.bidwinningbackend.service.lifecycle;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileIngestionService;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.KnowledgeFileMapper;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordLifecycleService;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordVectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 单 JVM Demo 的启动期 DB/MinIO/Chroma 对账与中断恢复编排。 */
@Service
public class FileLifecycleReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(
            FileLifecycleReconciliationService.class);

    private final CredentialFileMapper credentialFileMapper;
    private final KnowledgeFileMapper knowledgeFileMapper;
    private final BidRecordMapper bidRecordMapper;
    private final FileUploadService credentialFileService;
    private final KnowledgeFileIngestionService knowledgeFileService;
    private final BidRecordVectorIndexService bidRecordVectorIndexService;
    private final BidRecordLifecycleService bidRecordLifecycleService;

    public FileLifecycleReconciliationService(
            CredentialFileMapper credentialFileMapper,
            KnowledgeFileMapper knowledgeFileMapper,
            BidRecordMapper bidRecordMapper,
            FileUploadService credentialFileService,
            KnowledgeFileIngestionService knowledgeFileService,
            BidRecordVectorIndexService bidRecordVectorIndexService,
            BidRecordLifecycleService bidRecordLifecycleService) {
        this.credentialFileMapper = credentialFileMapper;
        this.knowledgeFileMapper = knowledgeFileMapper;
        this.bidRecordMapper = bidRecordMapper;
        this.credentialFileService = credentialFileService;
        this.knowledgeFileService = knowledgeFileService;
        this.bidRecordVectorIndexService = bidRecordVectorIndexService;
        this.bidRecordLifecycleService = bidRecordLifecycleService;
    }

    /** 仅在 JVM 启动时调用：把上一次进程的不可继续中间态改为明确可重试状态。 */
    public void prepareStartupRecovery() {
        bidRecordMapper.markInterruptedDeletionsFailed(
                "后端进程重启，自动继续投标记录删除");
        bidRecordMapper.markInterruptedRiskAnalysesFailed(
                "后端进程重启，风险分析已中断，请重新发起");
    }

    /**
     * 顺序收敛文件，再以数据库最终事实重建所有结构化投标记录。
     * 单条失败只记录，不阻塞其他文件；失败行保留稳定 ID 和可重试状态。
     */
    public ReconciliationReport reconcileAll() {
        List<String> failures = new ArrayList<>();
        int bidRecordDeletionCount = reconcileBidRecordDeletions(failures);
        int credentialCount = safelyReconcileCategory(
                "credential_file:list", failures, () -> reconcileCredentials(failures));
        int knowledgeCount = safelyReconcileCategory(
                "knowledge_file:list", failures, () -> reconcileKnowledge(failures));
        int bidRecordCount = safelyReconcileCategory(
                "bid_record:list", failures, () -> reconcileBidRecords(failures));
        ReconciliationReport report = new ReconciliationReport(
                bidRecordDeletionCount,
                credentialCount,
                knowledgeCount,
                bidRecordCount,
                List.copyOf(failures));
        if (failures.isEmpty()) {
            log.info(
                    "文件生命周期启动对账完成: bidRecordDeletion={}, credential={}, knowledge={}, bidRecord={}",
                    bidRecordDeletionCount,
                    credentialCount,
                    knowledgeCount,
                    bidRecordCount);
        } else {
            log.warn(
                    "文件生命周期启动对账完成但存在失败: bidRecordDeletion={}, credential={}, knowledge={}, "
                            + "bidRecord={}, failures={}",
                    bidRecordDeletionCount,
                    credentialCount,
                    knowledgeCount,
                    bidRecordCount,
                    failures);
        }
        return report;
    }

    private int reconcileBidRecordDeletions(List<String> failures) {
        return safelyReconcileCategory(
                "bid_record_deletion:list",
                failures,
                () -> {
                    List<BidRecord> records = bidRecordMapper.selectAll();
                    if (records == null) {
                        return 0;
                    }
                    int count = 0;
                    for (BidRecord record : records) {
                        if (record == null || record.getBidRecordId() == null
                                || record.getDeletionStatus() == null) {
                            continue;
                        }
                        count++;
                        try {
                            bidRecordLifecycleService.delete(
                                    record.getBidRecordId(), null);
                        } catch (RuntimeException failure) {
                            failures.add("bid_record_deletion:"
                                    + record.getBidRecordId() + ":" + message(failure));
                            log.error(
                                    "投标记录启动续删失败: bidRecordId={}",
                                    record.getBidRecordId(),
                                    failure);
                        }
                    }
                    return count;
                });
    }

    private int reconcileCredentials(List<String> failures) {
        List<CredentialFile> files = credentialFileMapper.selectAll();
        if (files == null) {
            return 0;
        }
        for (CredentialFile file : files) {
            try {
                credentialFileService.reconcileLifecycle(file);
            } catch (RuntimeException failure) {
                String id = file == null ? "null" : String.valueOf(file.getFileId());
                failures.add("credential_file:" + id + ":" + message(failure));
                log.error("资信文件启动对账失败: fileId={}", id, failure);
            }
        }
        return files.size();
    }

    private int reconcileKnowledge(List<String> failures) {
        List<KnowledgeFileRecord> files = knowledgeFileMapper.selectAll();
        if (files == null) {
            return 0;
        }
        for (KnowledgeFileRecord file : files) {
            try {
                knowledgeFileService.reconcileLifecycle(file);
            } catch (RuntimeException failure) {
                String id = file == null
                        ? "null"
                        : String.valueOf(file.getDocumentUuid());
                failures.add("knowledge_file:" + id + ":" + message(failure));
                log.error("知识文件启动对账失败: documentUuid={}", id, failure);
            }
        }
        return files.size();
    }

    private int reconcileBidRecords(List<String> failures) {
        List<BidRecord> records = bidRecordMapper.selectAll();
        if (records == null) {
            return 0;
        }
        for (BidRecord record : records) {
            try {
                if (record != null && record.getBidRecordId() != null) {
                    if (record.getDeletionStatus() != null) {
                        continue;
                    }
                    credentialFileService.reconcileBidRecordProjection(
                            record.getBidRecordId());
                    bidRecordVectorIndexService.reindex(record.getBidRecordId());
                }
            } catch (RuntimeException failure) {
                String id = record == null
                        ? "null"
                        : String.valueOf(record.getBidRecordId());
                failures.add("bid_record:" + id + ":" + message(failure));
                log.error("投标记录启动对账失败: bidRecordId={}", id, failure);
            }
        }
        return records.size();
    }

    private int safelyReconcileCategory(
            String category,
            List<String> failures,
            ReconciliationOperation operation) {
        try {
            return operation.run();
        } catch (RuntimeException failure) {
            failures.add(category + ":" + message(failure));
            log.error("生命周期对账类别失败: category={}", category, failure);
            return 0;
        }
    }

    private String message(Throwable failure) {
        String value = failure == null ? null : failure.getMessage();
        return value == null || value.isBlank()
                ? failure == null ? "unknown" : failure.getClass().getSimpleName()
                : value;
    }

    public record ReconciliationReport(
            int bidRecordDeletions,
            int credentialFiles,
            int knowledgeFiles,
            int bidRecords,
            List<String> failures) {
    }

    @FunctionalInterface
    private interface ReconciliationOperation {
        int run();
    }
}
