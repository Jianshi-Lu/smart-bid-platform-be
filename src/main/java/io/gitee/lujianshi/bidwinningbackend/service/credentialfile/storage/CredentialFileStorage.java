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
     * 将原始文件保存到指定上传批次。
     *
     * <p>调用方负责关闭输入流。</p>
     *
     * @param uploadBatchId   上传批次 UUID
     * @param originalFilename 原始文件名
     * @param input            文件内容输入流
     * @return 实际保存结果
     * @throws IOException 目录创建或文件写入失败
     */
    StoredFile storeOriginal(String uploadBatchId, String originalFilename, InputStream input) throws IOException;

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
     * @param originalFilename 规范化后的原始文件名
     * @param relativePath      相对于存储根目录的路径
     * @param fileSize          实际落盘大小，字节
     */
    record StoredFile(String originalFilename, String relativePath, long fileSize) {
    }
}
