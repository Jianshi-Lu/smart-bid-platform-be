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
     * @param uploadBatchId 已有批次 UUID；首次上传时为空
     * @return 上传批次号和文件持久化结果
     * @throws IllegalArgumentException 文件为空、文件名非法或批次号格式错误
     * @throws IllegalStateException 文件读写或摘要计算失败
     * @since 1.0
     */
    public CredentialFileUploadResponse upload(
            List<MultipartFile> files,
            String uploadBatchId) {
        validateFiles(files);
        String effectiveBatchId = resolveBatchId(uploadBatchId);

        List<String> createdPaths = new ArrayList<>();
        List<CredentialFileUploadItem> uploadedItems = new ArrayList<>();
        try {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                CredentialFileStorage.StoredFile storedFile = storeOriginal(effectiveBatchId, file);    // 存储原文件
                createdPaths.add(storedFile.relativePath());

                String sha256 = calculateDigest(storedFile.relativePath());
                uploadedItems.add(new CredentialFileUploadItem(
                        index,
                        storedFile.originalFilename(),
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
     * @param uploadBatchId 已确定的上传批次 UUID
     * @param file 待保存的 Multipart 文件
     * @return 文件存储结果
     * @throws IOException 文件流读取或持久化失败
     */
    private CredentialFileStorage.StoredFile storeOriginal(
            String uploadBatchId,
            MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return credentialFileStorage.storeOriginal(
                    uploadBatchId,
                    file.getOriginalFilename(),
                    input);
        }
    }

    /**
     * 重新读取已落盘文件并计算其内容摘要。
     *
     * @param relativePath 文件存储相对路径
     * @return 小写十六进制摘要
     * @throws IOException 文件重新读取或摘要计算失败
     */
    private String calculateDigest(String relativePath) throws IOException {
        try (InputStream input = credentialFileStorage.open(relativePath)) {
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
