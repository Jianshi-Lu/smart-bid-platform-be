package io.gitee.lujianshi.bidwinningbackend.service.credentialfile;

import io.gitee.lujianshi.bidwinningbackend.dto.credentialfile.CredentialFileUploadItem;
import io.gitee.lujianshi.bidwinningbackend.dto.credentialfile.CredentialFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.enums.CredentialFileConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage.CredentialFileStorage;
import io.gitee.lujianshi.bidwinningbackend.service.filedigest.FileDigestCalculator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 资信文件上传编排 Service。
 *
 * <p>负责批次号、输入校验、存储调用、摘要计算和失败补偿，不依赖具体文件系统实现。</p>
 *
 * @since 1.0
 */
@Service
public class CredentialFileUploadService {

    @Resource
    private CredentialFileStorage credentialFileStorage;

    @Resource(name = "sha256FileDigestCalculator")
    private FileDigestCalculator fileDigestCalculator;

    /**
     * 保存一次请求中的全部原始资信文件。
     *
     * <p>任一文件保存或摘要计算失败时，仅补偿删除本次请求已经创建的文件。</p>
     *
     * @param files         本次上传的文件列表
     * @param bidRecordId   文件所属的投标记录 ID
     * @param uploadBatchId 已有批次 UUID；首次上传时为空
     * @return 上传批次号和文件持久化结果
     * @throws IllegalArgumentException 投标记录 ID、文件或批次号格式不正确
     * @throws IllegalStateException 文件读写或摘要计算失败
     * @since 1.0
     */
    public CredentialFileUploadResponse upload(
            List<MultipartFile> files,
            Integer bidRecordId,
            String uploadBatchId) {
        validateBidRecordId(bidRecordId);
        validateFiles(files);
        String effectiveBatchId = resolveBatchId(uploadBatchId);

        List<String> createdPaths = new ArrayList<>();
        List<CredentialFileUploadItem> uploadedItems = new ArrayList<>();
        try {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                String sha256 = calculateDigest(file);

                // 上传文件入口：携带原始内容摘要落盘
                CredentialFileStorage.StoredFile storedFile =
                        storeOriginal(bidRecordId, effectiveBatchId, file, sha256);
                createdPaths.add(storedFile.relativePath());

                uploadedItems.add(new CredentialFileUploadItem(
                        index,
                        storedFile.safeOriginalFilename(),
                        file.getContentType(),
                        storedFile.fileSize(),
                        sha256,
                        storedFile.relativePath(),
                        CredentialFileConvertStatus.UPLOADED));
            }
        } catch (IllegalArgumentException e) {
            cleanup(createdPaths);
            throw e;
        } catch (IOException | RuntimeException e) {
            cleanup(createdPaths);
            throw new IllegalStateException("资信文件上传失败", e);
        }

        return new CredentialFileUploadResponse(effectiveBatchId, uploadedItems);
    }

    /**
     * 打开 Multipart 文件流并委托存储端口完成原文件持久化。
     *
     * @param bidRecordId 文件所属的投标记录 ID
     * @param uploadBatchId 已确定的上传批次 UUID
     * @param file 待保存的 Multipart 文件
     * @param sha256 原始上传内容的 SHA-256 摘要
     * @return 文件存储结果
     * @throws IOException 文件流读取或持久化失败
     */
    private CredentialFileStorage.StoredFile storeOriginal(
            Integer bidRecordId,
            String uploadBatchId,
            MultipartFile file,
            String sha256) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return credentialFileStorage.storeOriginal( // 由配置激活的存储实现执行保存
                    new CredentialFileStorage.StoreOriginalRequest(
                            bidRecordId,
                            uploadBatchId,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            sha256,
                            input));
        }
    }

    /**
     * 校验上传请求携带的投标记录 ID 基本格式。
     *
     * <p>当前阶段尚未接入数据库，仅校验正整数；记录存在性将在落库阶段补充。</p>
     *
     * @param bidRecordId 文件所属的投标记录 ID
     * @throws IllegalArgumentException ID 为空或不是正整数
     */
    private void validateBidRecordId(Integer bidRecordId) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new IllegalArgumentException("投标记录 ID 不正确");
        }
    }

    /**
     * 在持久化前读取上传文件并计算其内容摘要。
     *
     * @param file 待计算摘要的上传文件
     * @return 小写十六进制摘要
     * @throws IOException 上传文件读取或摘要计算失败
     */
    private String calculateDigest(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return fileDigestCalculator.calculate(input);
        }
    }

    /**
     * 校验上传集合及每个文件的基本有效性。
     *
     * @param files 本次上传的文件列表
     * @throws IllegalArgumentException 集合为空、文件为空或原始文件名为空
     */
    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("上传文件不能为空");
            }
            if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
                throw new IllegalArgumentException("原始文件名不能为空");
            }
        }
    }

    /**
     * 首次上传时生成批次 UUID，追加上传时校验并规范化已有 UUID。
     *
     * @param uploadBatchId 前端传入的可选批次号
     * @return 可用于存储路径的标准 UUID 字符串
     * @throws IllegalArgumentException 已有批次号格式不正确
     */
    private String resolveBatchId(String uploadBatchId) {
        if (uploadBatchId == null || uploadBatchId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        try {
            return UUID.fromString(uploadBatchId).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("上传批次号格式不正确", e);
        }
    }

    /**
     * 按创建顺序的逆序尽力删除本次请求产生的文件。
     *
     * <p>补偿失败不会覆盖原始上传异常，也不会删除同批次此前已存在的文件。</p>
     *
     * @param createdPaths 本次请求已经成功创建的相对路径
     */
    private void cleanup(List<String> createdPaths) {
        for (String relativePath : createdPaths.reversed()) {
            try {
                credentialFileStorage.delete(relativePath);
            } catch (IOException | RuntimeException ignored) {
                // 补偿清理失败不覆盖原始上传异常，后续可接入日志与定期清理任务。
            }
        }
    }
}
