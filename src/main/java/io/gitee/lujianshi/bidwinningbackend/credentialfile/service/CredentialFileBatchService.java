package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.CredentialFileBatchStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileBatchMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 资信文件上传批次 Service。
 *
 * <p>负责创建后端生成的批次 UUID、分配投标记录下的批次序号，以及在全部原始文件成功后封口批次。</p>
 *
 * @since V22
 */
@Service
public class CredentialFileBatchService {

    private static final int MAX_RETRY = 3;

    @Resource
    private CredentialFileBatchMapper mapper;

    /**
     * 创建一个处于上传中的资信文件批次。
     *
     * @param bidRecordId 文件所属投标记录 ID
     * @param fileCount 本批次文件数量
     * @return 已生成 UUID 和批次序号的批次实体
     * @throws DuplicateKeyException 批次序号并发冲突且重试耗尽
     */
    public CredentialFileBatch createUploadingBatch(
            Integer bidRecordId,
            int fileCount) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new IllegalArgumentException("投标记录 ID 不正确");
        }
        if (fileCount <= 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            int nextBatchNo = nextBatchNo(bidRecordId);
            CredentialFileBatch batch = new CredentialFileBatch();
            batch.setUploadBatchId(UUID.randomUUID().toString());
            batch.setBidRecordId(bidRecordId);
            batch.setBatchNo(nextBatchNo);
            batch.setBatchStatus(CredentialFileBatchStatus.UPLOADING);
            batch.setFileCount(fileCount);
            batch.setConvertedFileCount(0);
            batch.setCreatedAt(LocalDateTime.now());

            try {
                int rows = mapper.insert(batch);
                if (rows != 1) {
                    throw new IllegalStateException("资信文件上传批次创建失败");
                }
                return batch;
            } catch (DuplicateKeyException e) {
                if (attempt == MAX_RETRY) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("资信文件上传批次创建失败");
    }

    /**
     * 将批次标记为原始文件已经全部上传并校验成功。
     *
     * @param uploadBatchId 上传批次 UUID
     */
    public void markUploaded(String uploadBatchId) {
        if (uploadBatchId == null || uploadBatchId.isBlank()) {
            throw new IllegalArgumentException("上传批次号不能为空");
        }
        int rows = mapper.markUploaded(
                uploadBatchId,
                CredentialFileBatchStatus.UPLOADED.name(),
                LocalDateTime.now());
        if (rows != 1) {
            throw new IllegalStateException("资信文件上传批次封口失败");
        }
    }

    /** 获取指定投标记录下一个批次序号。 */
    private int nextBatchNo(Integer bidRecordId) {
        Integer maxBatchNo = mapper.selectMaxBatchNo(bidRecordId);
        return maxBatchNo == null ? 1 : maxBatchNo + 1;
    }
}
