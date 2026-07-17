package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 基于本地文件系统的资信文件存储实现。
 *
 * <p>物理目录结构为 {@code {root}/{uploadBatchId}/original/}，对外只返回相对路径。</p>
 *
 * @since 1.0
 */
@Service
public class LocalCredentialFileStorage implements CredentialFileStorage {

    private final Path storageRoot;

    /**
     * 创建本地存储实现。
     *
     * @param localRoot 本地存储根目录
     * @throws IllegalArgumentException 根目录为空时抛出
     */
    public LocalCredentialFileStorage(
            @Value("${credential-file.storage.local-root}") String localRoot) {
        if (localRoot == null || localRoot.isBlank()) {
            throw new IllegalArgumentException("资信文件存储根目录不能为空");
        }
        this.storageRoot = Path.of(localRoot).toAbsolutePath().normalize();
    }

    /**
     * 将原始文件写入批次的 {@code original} 目录，并以 UUID 前缀避免同名覆盖。
     *
     * @param uploadBatchId 上传批次 UUID
     * @param originalFilename 用户上传时的原始文件名
     * @param input 文件内容输入流
     * @return 规范化文件名、存储相对路径和实际文件大小
     * @throws IOException 目录创建、文件写入或文件大小读取失败
     */
    @Override
    public StoredFile storeOriginal(
            String uploadBatchId,
            String originalFilename,
            InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("文件流不能为空");
        }

        String safeOriginalFilename = normalizeFilename(originalFilename);
        String storedFilename = UUID.randomUUID() + "_" + safeOriginalFilename;
        String relativePath = uploadBatchId + "/original/" + storedFilename;
        Path targetPath = resolveSafely(relativePath);

        Files.createDirectories(targetPath.getParent());
        boolean targetCreated = false;
        try (OutputStream output = Files.newOutputStream(
                targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            targetCreated = true;
            input.transferTo(output);
        } catch (IOException e) {
            if (targetCreated) {
                Files.deleteIfExists(targetPath);
            }
            throw e;
        }

        return new StoredFile(safeOriginalFilename, relativePath, Files.size(targetPath));
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
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("文件相对路径不能为空");
        }
        Path resolvedPath = storageRoot.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(storageRoot)) {
            throw new IllegalArgumentException("文件路径超出存储根目录");
        }
        return resolvedPath;
    }

    /**
     * 去除客户端路径片段和控制字符，得到可用于物理存储的安全文件名。
     *
     * @param originalFilename 客户端提供的原始文件名
     * @return 规范化后的文件名
     * @throws IllegalArgumentException 文件名为空或规范化后不合法
     */
    private String normalizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("原始文件名不能为空");
        }

        String unifiedFilename = originalFilename.replace('\\', '/');
        String filename = unifiedFilename.substring(unifiedFilename.lastIndexOf('/') + 1).trim();
        filename = filename.replaceAll("[\\p{Cntrl}]", "_");
        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            throw new IllegalArgumentException("原始文件名不合法");
        }
        return filename;
    }
}
