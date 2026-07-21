package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.config.storage.CredentialFileStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 基于本地文件系统的资信文件存储实现。
 *
 * <p>物理目录结构为 {@code {root}/{uploadBatchId}/original/}，对外只返回相对路径。</p>
 * <p>未配置存储类型或明确配置为 {@code LOCAL} 时，由 Spring 注册为存储端口实现。</p>
 *
 * @since 1.0
 */
@Service
@ConditionalOnProperty(
        prefix = "credential-file.storage",
        name = "type",
        havingValue = "LOCAL",
        matchIfMissing = true)
public class LocalCredentialFileStorage implements CredentialFileStorage {

    private final Path storageRoot;

    /**
     * 创建本地存储实现。
     *
     * @param properties 资信文件存储配置，必须包含本地存储根目录
     * @throws IllegalArgumentException 根目录为空时抛出
     */
    public LocalCredentialFileStorage(CredentialFileStorageProperties properties) {
        String localRoot = properties.getLocal().getRoot();
        if (localRoot == null || localRoot.isBlank()) {
            throw new IllegalArgumentException("资信文件存储根目录不能为空");
        }
        this.storageRoot = Path.of(localRoot).toAbsolutePath().normalize();
    }

    /**
     * 将原始文件写入批次的 {@code original} 目录，并以 UUID 前缀避免同名覆盖。
     *
     * @param request 原始文件保存请求
     * @return 规范化文件名、存储相对路径和实际文件大小
     * @throws IOException 目录创建、文件写入或文件大小读取失败
     */
    @Override
    public StoredFile storeOriginal(StoreOriginalRequest request) throws IOException {
        CredentialFileStorageKey.OriginalFileKey fileKey =
                CredentialFileStorageKey.createOriginalFileKey(
                        request.uploadBatchId(), request.originalFilename());
        Path targetPath = resolveSafely(fileKey.relativePath());

        Files.createDirectories(targetPath.getParent());
        boolean targetCreated = false;
        try {
            try (OutputStream output = Files.newOutputStream(
                    targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                targetCreated = true;
                request.input().transferTo(output);
            }

            long actualFileSize = Files.size(targetPath);
            if (actualFileSize != request.contentLength()) {
                throw new IOException("文件实际大小与声明长度不一致");
            }
            return new StoredFile(
                    fileKey.safeOriginalFilename(), fileKey.relativePath(), actualFileSize);
        } catch (IOException e) {
            if (targetCreated) {
                Files.deleteIfExists(targetPath);
            }
            throw e;
        }
    }

    /**
     * 根据存储相对路径打开本地文件输入流。
     *
     * @param relativePath 相对于存储根目录的文件路径
     * @return 文件输入流，由调用方关闭
     * @throws IOException 文件不存在或无法读取
     */
    @Override
    public InputStream open(String relativePath) throws IOException {
        Path filePath = resolveSafely(relativePath);
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("资信文件不存在: " + relativePath);
        }
        return Files.newInputStream(filePath, StandardOpenOption.READ);
    }

    /**
     * 删除相对路径对应的本地文件，文件不存在时保持幂等。
     *
     * @param relativePath 相对于存储根目录的文件路径
     * @throws IOException 文件删除失败
     */
    @Override
    public void delete(String relativePath) throws IOException {
        Files.deleteIfExists(resolveSafely(relativePath));
    }

    /**
     * 将相对路径解析为绝对路径，并确保结果没有逃逸存储根目录。
     *
     * @param relativePath 相对于存储根目录的路径
     * @return 规范化后的安全绝对路径
     * @throws IllegalArgumentException 路径为空或超出存储根目录
     */
    private Path resolveSafely(String relativePath) {
        String normalizedPath = CredentialFileStorageKey.normalizeRelativePath(relativePath);
        Path resolvedPath = storageRoot.resolve(normalizedPath).normalize();
        if (!resolvedPath.startsWith(storageRoot)) {
            throw new IllegalArgumentException("文件路径超出存储根目录");
        }
        return resolvedPath;
    }
}
