package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 资信文件存储抽象。
 *
 * <p>上传编排只依赖该接口；后续迁移 MinIO 时可提供新的实现而无需修改 Controller。</p>
 *
 * @since 1.0
 */
public interface CredentialFileStorage {

    /**
     * 原始文件保存请求。
     *
     * @param uploadBatchId 上传批次 UUID
     * @param originalFilename 客户端提供的原始文件名
     * @param contentType 文件 MIME 类型，未知时可以为空
     * @param contentLength 文件内容长度，字节
     * @param input 文件内容输入流，由调用方关闭
     */
    record StoreOriginalRequest(
            String uploadBatchId,
            String originalFilename,
            String contentType,
            long contentLength,
            InputStream input) {

        /** 校验所有存储实现共同依赖的请求边界。 */
        public StoreOriginalRequest {
            if (uploadBatchId == null || uploadBatchId.isBlank()) {
                throw new IllegalArgumentException("上传批次号不能为空");
            }
            if (originalFilename == null || originalFilename.isBlank()) {
                throw new IllegalArgumentException("原始文件名不能为空");
            }
            if (contentLength < 0) {
                throw new IllegalArgumentException("文件长度不能为负数");
            }
            if (input == null) {
                throw new IllegalArgumentException("文件流不能为空");
            }
        }
    }

    /**
     * 将原始文件保存到指定上传批次。
     *
     * <p>调用方负责关闭输入流。</p>
     *
     * @param request 原始文件保存请求
     * @return 实际保存结果
     * @throws IOException 目录创建或文件写入失败
     */
    StoredFile storeOriginal(StoreOriginalRequest request) throws IOException;

    /**
     * 根据存储相对路径打开文件流。
     *
     * @param relativePath 存储相对路径
     * @return 文件输入流，由调用方关闭
     * @throws IOException 文件不存在或无法读取
     */
    InputStream open(String relativePath) throws IOException;

    /**
     * 删除指定相对路径的文件。
     *
     * @param relativePath 存储相对路径
     * @throws IOException 文件删除失败
     */
    void delete(String relativePath) throws IOException;

    /**
     * 文件持久化结果。
     *
     * @param safeOriginalFilename 经过路径剥离和字符清理后的安全原始文件名
     * @param relativePath      相对于存储根目录的路径
     * @param fileSize          实际落盘大小，字节
     */
    record StoredFile(String safeOriginalFilename, String relativePath, long fileSize) {
    }
}
