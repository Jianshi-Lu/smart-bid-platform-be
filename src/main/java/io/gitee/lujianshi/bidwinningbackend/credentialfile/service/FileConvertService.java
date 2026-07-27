package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonFileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.BatchConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.BidRecordCredentialConversionStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileBatchMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.entity.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.service.BidRecordService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 按 bid_record 编排资信文件批次转换。 */
@Service
public class FileConvertService {

    private static final String PYTHON_FILE_COMPLETED = "COMPLETED";

    @Resource
    private CredentialFileBatchMapper batchMapper;

    @Resource
    private CredentialFileMapper fileMapper;

    @Resource
    private ToMarkdownClient toMarkdownClient;

    @Resource
    private BidRecordService bidRecordService;

    @Resource
    private BidRecordMapper bidRecordMapper;

    /** 通过 Spring 代理开启路径汇总事务，避免同类调用绕过事务注解。 */
    @Resource
    @Lazy
    private FileConvertService self;

    /**
     * 无差别重新处理指定投标记录下的全部批次，并返回批次级汇总状态。
     *
     * <p>该接口不根据批次或文件状态筛选转换对象。每次转换都使用原始文件对应的
     * converted object key，重复转换会覆盖原 Markdown 对象。</p>
     */
    public FileConvertResponse convertAllBatches(Integer bidRecordId) {
        validateBidRecord(bidRecordId);

        List<CredentialFileBatch> batches = batchMapper.selectByBidRecordId(bidRecordId);
        if (batches == null || batches.isEmpty()) {
            return new FileConvertResponse(
                    bidRecordId,
                    BidRecordCredentialConversionStatus.ALL_CONVERTED,
                    "该投标记录暂无资信材料批次",
                    0,
                    0,
                    0,
                    0,
                    List.of());
        }

        for (CredentialFileBatch batch : batches) {
            convertBatch(batch);
        }

        List<CredentialFileBatch> latestBatches =
                batchMapper.selectByBidRecordId(bidRecordId);
        if (latestBatches == null) {
            latestBatches = List.of();
        }

        int convertedCount = countByStatus(latestBatches, ConvertStatus.CONVERTED);
        int failedCount = countByStatus(latestBatches, ConvertStatus.FAILED);
        int processingCount = latestBatches.size() - convertedCount - failedCount;

        BidRecordCredentialConversionStatus overallStatus;
        String message;
        if (failedCount > 0) {
            overallStatus = BidRecordCredentialConversionStatus.PARTIAL_FAILED;
            message = "部分资信材料批次转换失败";
        } else if (convertedCount == latestBatches.size()) {
            overallStatus = BidRecordCredentialConversionStatus.ALL_CONVERTED;
            message = "该投标记录的所有资信材料批次已经完成转换";
        } else {
            overallStatus = BidRecordCredentialConversionStatus.PROCESSING;
            message = "部分资信材料批次仍在处理中";
        }

        return new FileConvertResponse(
                bidRecordId,
                overallStatus,
                message,
                latestBatches.size(),
                convertedCount,
                processingCount,
                failedCount,
                toBatchResponses(latestBatches));
    }

    /** 抢占一个批次，并由 Java 逐文件编排转换和状态更新。 */
    private void convertBatch(CredentialFileBatch batch) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = batchMapper.claimConverting(batch.getUploadBatchId(), now);
        if (claimed != 1) {
            return;
        }

        int convertedFileCount = 0;
        try {
            List<CredentialFile> files = fileMapper.selectByUploadBatchId(
                    batch.getUploadBatchId());
            if (files == null || files.size() != batch.getFileCount()) {
                throw new IllegalStateException("资信文件批次明细数量不一致");
            }

            List<String> failures = new ArrayList<>();
            for (CredentialFile file : files) {
                int fileClaimed = fileMapper.markConverting(file.getFileId());
                if (fileClaimed != 1) {
                    // 没有获得文件处理权不代表转换失败，不能触发批次级失败处理。
                    // 循环结束后通过最新数据库状态决定批次是否仍在处理中。
                    continue;
                }

                PythonFileConvertResponse response;
                try {
                    response =
                            toMarkdownClient.convertObject(file.getOriginalFilePath());
                    validateFileResponse(file, response);
                } catch (RuntimeException failure) {
                    String message = toErrorMessage(failure);
                    int rows = fileMapper.markFailed(file.getFileId(), message);
                    if (rows != 1) {
                        throw new IllegalStateException(
                                "资信文件失败状态更新失败: " + file.getFileId(),
                                failure);
                    }
                    failures.add(file.getOriginalFilename() + ": " + message);
                    continue;
                }

                int rows = fileMapper.markConverted(
                        file.getFileId(),
                        response.convertedObjectKey(),
                        LocalDateTime.now());
                if (rows != 1) {
                    throw new IllegalStateException(
                            "资信文件完成状态更新失败: " + file.getFileId());
                }
                convertedFileCount++;

                // 单文件转换成功后立即同步当前 bid_record 下已完成文件的 Markdown 路径。
                // 回写失败属于批次编排失败，不再把已经转换成功的文件改记为 FAILED。
                self.syncBidRecordCredentials(batch.getBidRecordId());
            }

            finalizeBatch(batch, failures);
        } catch (RuntimeException failure) {
            fileMapper.markFailedByUploadBatchId(
                    batch.getUploadBatchId(),
                    toErrorMessage(failure));
            markBatchFailed(
                    batch.getUploadBatchId(),
                    convertedFileCount,
                    failure.getMessage());
        }
    }

    /** 根据文件的最新状态收敛批次状态，避免使用本次请求的局部计数作判断。 */
    private void finalizeBatch(
            CredentialFileBatch batch,
            List<String> failures) {
        List<CredentialFile> latestFiles = fileMapper.selectByUploadBatchId(
                batch.getUploadBatchId());
        if (latestFiles == null || latestFiles.size() != batch.getFileCount()) {
            throw new IllegalStateException("资信文件批次明细数量不一致");
        }

        int convertedFileCount = (int) latestFiles.stream()
                .filter(file -> file.getStatus() == ConvertStatus.CONVERTED)
                .count();
        boolean hasFailed = latestFiles.stream()
                .anyMatch(file -> file.getStatus() == ConvertStatus.FAILED);
        boolean hasProcessing = latestFiles.stream()
                .anyMatch(file -> file.getStatus() == ConvertStatus.UPLOADING
                        || file.getStatus() == ConvertStatus.UPLOADED
                        || file.getStatus() == ConvertStatus.CONVERTING);

        if (hasFailed) {
            String message = failures.isEmpty()
                    ? latestFiles.stream()
                    .filter(file -> file.getStatus() == ConvertStatus.FAILED)
                    .map(file -> file.getOriginalFilename() + ": "
                            + toErrorMessage(file.getErrorMessage()))
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("资信文件转换失败")
                    : String.join("; ", failures);
            markBatchFailed(
                    batch.getUploadBatchId(),
                    convertedFileCount,
                    message);
            return;
        }

        if (hasProcessing) {
            // 仍有文件由其他任务处理，保留批次 CONVERTING 状态。
            return;
        }

        if (convertedFileCount != batch.getFileCount()) {
            throw new IllegalStateException("资信文件批次状态不一致");
        }

        int rows = batchMapper.markConverted(
                batch.getUploadBatchId(),
                convertedFileCount,
                LocalDateTime.now());
        if (rows != 1) {
            throw new IllegalStateException("资信文件批次完成状态更新失败");
        }
    }

    /** 校验 Python 返回的单文件转换结果。 */
    private void validateFileResponse(
            CredentialFile file,
            PythonFileConvertResponse response) {
        if (response == null) {
            throw new IllegalStateException("Python 转换响应为空");
        }
        if (!PYTHON_FILE_COMPLETED.equals(response.status())
                || response.originalObjectKey() == null
                || !response.originalObjectKey().equals(file.getOriginalFilePath())
                || response.convertedObjectKey() == null
                || response.convertedObjectKey().isBlank()) {
            throw new IllegalStateException("Python 单文件转换未完成");
        }
    }

    /** 将批次失败原因截断到数据库字段长度以内。 */
    private void markBatchFailed(
            String uploadBatchId,
            int convertedFileCount,
            String errorMessage) {
        String message = toErrorMessage(errorMessage);
        batchMapper.markFailed(uploadBatchId, convertedFileCount, message);
    }

    private String toErrorMessage(Throwable failure) {
        return toErrorMessage(failure == null ? null : failure.getMessage());
    }

    private String toErrorMessage(String errorMessage) {
        String message = errorMessage == null || errorMessage.isBlank()
                ? "资信文件转换失败"
                : errorMessage;
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        return message;
    }

    /**
     * 将当前投标记录下已完成文件的 Markdown object key 列表写回 bid_record。
     *
     * <p>先锁定 bid_record 行，再查询文件路径并整体更新 JSON，避免两个批次并发转换时发生
     * 读改写覆盖。事务只包含数据库查询和更新，不包含 Python 调用。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncBidRecordCredentials(Integer bidRecordId) {
        if (bidRecordMapper.selectByIdForUpdate(bidRecordId) == null) {
            throw new IllegalStateException("投标记录不存在: " + bidRecordId);
        }

        List<CredentialFile> files = fileMapper.selectByBidRecordId(bidRecordId);
        if (files == null || files.isEmpty()) {
            return;
        }

        List<String> markdownPaths = files.stream()
                .filter(file -> file.getStatus() == ConvertStatus.CONVERTED)
                .map(CredentialFile::getMarkdownFilePath)
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .toList();

        // 当前可能仍有文件处于上传、转换或失败状态，暂时没有可回写的路径时保留原值。
        if (markdownPaths.isEmpty()) {
            return;
        }

        BidRecord patch = new BidRecord();
        patch.setBidRecordId(bidRecordId);
        patch.setCredentials(markdownPaths);
        if (bidRecordMapper.updateById(patch) != 1) {
            throw new IllegalStateException("投标记录资信材料路径更新失败");
        }
    }

    private void validateBidRecord(Integer bidRecordId) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new IllegalArgumentException("投标记录 ID 不正确");
        }
        if (bidRecordService.getById(bidRecordId) == null) {
            throw new IllegalArgumentException("投标记录不存在");
        }
    }

    private int countByStatus(
            List<CredentialFileBatch> batches,
            ConvertStatus status) {
        return (int) batches.stream()
                .filter(batch -> batch.getStatus() == status)
                .count();
    }

    private List<BatchConvertResponse> toBatchResponses(
            List<CredentialFileBatch> batches) {
        List<BatchConvertResponse> responses = new ArrayList<>();
        for (CredentialFileBatch batch : batches) {
            String message = switch (batch.getStatus()) {
                case UPLOADING -> "文件仍在上传中";
                case UPLOADED -> "等待转换";
                case CONVERTING -> "正在转换";
                case CONVERTED -> "转换完成";
                case FAILED -> batch.getErrorMessage() == null
                        ? "转换失败"
                        : batch.getErrorMessage();
            };
            responses.add(new BatchConvertResponse(
                    batch.getUploadBatchId(),
                    batch.getBatchNo(),
                    batch.getStatus(),
                    batch.getFileCount(),
                    batch.getConvertedFileCount(),
                    message));
        }
        return Collections.unmodifiableList(responses);
    }
}
