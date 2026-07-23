package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.CredentialFileStorageProperties;
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
 * <p>调用方负责提供逻辑相对路径，本实现只负责安全解析、目录创建、写入和大小确认。</p>
 *
 * @since 1.0
 */
@Service
@ConditionalOnProperty(
        prefix = "credential-file.storage",
        name = "type",
        havingValue = "LOCAL",
        matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path storageRoot;

    /**
     * 创建本地存储实现。
     *
     * @param properties 资信文件存储配置，必须包含本地存储根目录
     * @throws IllegalArgumentException 根目录为空时抛出
     */
    public LocalFileStorage(CredentialFileStorageProperties properties) {
        String localRoot = properties.getLocal().getRoot();
        if (localRoot == null || localRoot.isBlank()) {
            throw new IllegalArgumentException("资信文件存储根目录不能为空");
        }
        this.storageRoot = Path.of(localRoot).toAbsolutePath().normalize();
    }

    /**
     * 将文件写入调用方指定的逻辑相对路径。
     *
     * @param request 通用文件保存请求
     * @return 存储端确认的文件结果
     * @throws IOException 目录创建、文件写入或文件大小读取失败
     */
    @Override
    public StoredFile store(StoreRequest request) throws IOException {
        String relativePath = StoragePath.normalizeRelativePath(request.relativePath());
        Path targetPath = resolveSafely(relativePath);

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
                    relativePath,
                    actualFileSize,
                    request.contentType(),
                    request.metadata(),
                    null);
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

    /** 将相对路径解析为存储根目录内的安全绝对路径。 */
    private Path resolveSafely(String relativePath) {
        String normalizedPath = StoragePath.normalizeRelativePath(relativePath);
        Path resolvedPath = storageRoot.resolve(normalizedPath).normalize();
        if (!resolvedPath.startsWith(storageRoot)) {
            throw new IllegalArgumentException("文件路径超出存储根目录");
        }
        return resolvedPath;
    }
}
