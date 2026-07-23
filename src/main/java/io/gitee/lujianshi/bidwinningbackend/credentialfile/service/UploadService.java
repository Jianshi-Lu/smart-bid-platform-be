package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.CredentialFileConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.PendingUploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatchContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity.FileIntegrityException;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity.VerifiedStoredFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.PendingUploadItemProcessor;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 资信文件上传批次编排 Service。
 *
 * <p>负责解析批次上下文、按请求顺序处理文件、收集验证结果和执行批次级失败补偿；
 * 单文件校验和计算、持久化及回读验证由 {@link PendingUploadItemProcessor} 完成。</p>
 *
 * @since 1.0
 */
@Service
public class UploadService {

    @Resource
    private PendingUploadItemProcessor pendingUploadItemProcessor;

    @Resource
    private CredentialFileBatchService credentialFileBatchService;

    @Resource
    private FileStorage fileStorage;

    /**
     * 处理一次请求中的全部原始资信文件。
     *
     * <p>批次 UUID 在本方法开始时由后端生成并写入批次表。任一条目失败时，当前文件由处理器
     * 补偿，本服务删除本次请求中此前已经验证成功的文件；只有全部文件成功后才封口批次。</p>
     *
     * @param batch Controller 转换后的内部上传任务
     * @return 上传批次号和文件持久化结果
     * @throws IllegalArgumentException 批次、文件或批次号格式不正确
     * @throws FileIntegrityException 已存储正文完整性校验失败
     * @throws IllegalStateException 文件读写或校验和计算失败
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResponse upload(UploadBatch batch) {
        if (batch == null) {
            throw new IllegalArgumentException("上传批次不能为空");
        }
        CredentialFileBatch batchRecord = credentialFileBatchService.createUploadingBatch(
                batch.bidRecordId(), batch.items().size());
        UploadBatchContext context = new UploadBatchContext(
                batch.bidRecordId(), batchRecord.getUploadBatchId());

        List<String> verifiedPaths = new ArrayList<>();
        List<UploadResponse.UploadedFile> responseItems = new ArrayList<>();
        try {
            // 将每一个UploadBatch中的Item交给processor处理：
            // 1. 循环全部成功：返回所有成功存储的文件——controller
            // 2. 循环有一个失败：清空所有成功存储的文件，抛出异常
            for (int index = 0; index < batch.items().size(); index++) {
                PendingUploadItem item = batch.items().get(index);
                VerifiedStoredFile verifiedFile =
                        pendingUploadItemProcessor.process(context, item);
                verifiedPaths.add(verifiedFile.relativePath());
                responseItems.add(toResponseItem(index, item, verifiedFile));
            }
            credentialFileBatchService.markUploaded(context.uploadBatchId());
        } catch (IllegalArgumentException | FileIntegrityException failure) {
            cleanup(verifiedPaths, failure);
            throw failure;
        } catch (IOException | RuntimeException failure) {
            cleanup(verifiedPaths, failure);
            throw new IllegalStateException("资信文件上传失败", failure);
        }

        return new UploadResponse(context.uploadBatchId(), responseItems);
    }

    /** 将已经验证的存储结果转换为对外响应条目。 */
    private UploadResponse.UploadedFile toResponseItem(
            int fileOrder,
            PendingUploadItem item,
            VerifiedStoredFile verifiedFile) {
        String contentType = verifiedFile.contentType() == null
                ? item.contentType()
                : verifiedFile.contentType();
        return new UploadResponse.UploadedFile(
                fileOrder,
                verifiedFile.safeOriginalFilename(),
                contentType,
                verifiedFile.fileSize(),
                verifiedFile.sha256(),
                verifiedFile.relativePath(),
                CredentialFileConvertStatus.UPLOADED);
    }

    /** 按逆序尽力删除本次请求此前已验证成功的文件。 */
    private void cleanup(
            List<String> verifiedPaths,
            Throwable originalFailure) {
        for (String relativePath : verifiedPaths.reversed()) {
            try {
                fileStorage.delete(relativePath);
            } catch (IOException | RuntimeException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
    }
}
