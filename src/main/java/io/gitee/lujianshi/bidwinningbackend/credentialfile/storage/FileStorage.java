package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 资信文件持久化存储抽象。
 *
 * <p>上层流程只依赖保存、读取和删除能力，不感知本地文件系统或 MinIO SDK。</p>
 *
 * @since 1.0
 */
public interface FileStorage {
    /**
     * 已写入持久化介质的纯存储结果。
     *
     * @param relativePath 相对于存储根目录的路径或对象存储 Key
     * @param fileSize 存储端确认的文件大小，字节
     * @param contentType 存储端确认的 MIME 类型，未知时可以为空
     * @param metadata 存储端确认的调用方 metadata；存储层不解释字段含义
     * @param etag 对象存储 ETag；本地存储或无法提供时为空
     */
    record StoredFile(
            String relativePath,
            long fileSize,
            String contentType,
            Map<String, String> metadata,
            String etag) {

        /** 创建不可变的存储属性快照。 */
        public StoredFile {
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("文件相对路径不能为空");
            }
            if (fileSize < 0) {
                throw new IllegalArgumentException("已存储文件大小不能为负数");
            }
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    /**
     * 文件读取请求。
     *
     * @param relativePath 存储相对路径或对象存储 Key
     * @param matchEtag 期望读取的对象 ETag；本地存储或无需条件读取时为空
     */
    record OpenRequest(String relativePath, String matchEtag) {

        /** 校验所有存储实现共同依赖的读取边界。 */
        public OpenRequest {
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("文件相对路径不能为空");
            }
        }
    }

    /**
     * 通用文件保存请求。
     *
     * @param relativePath 调用方确定的逻辑存储路径
     * @param contentType 文件 MIME 类型，未知时可以为空
     * @param contentLength 文件内容长度，字节
     * @param metadata 需要随文件保存的调用方 metadata；存储层仅负责透传
     * @param input 文件内容输入流，由调用方关闭
     */
    record StoreRequest(
            String relativePath,
            String contentType,
            long contentLength,
            Map<String, String> metadata,
            InputStream input) {

        /** 校验所有存储实现共同依赖的保存请求边界。 */
        public StoreRequest {
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("文件相对路径不能为空");
            }
            if (contentLength < 0) {
                throw new IllegalArgumentException("文件长度不能为负数");
            }
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            if (input == null) {
                throw new IllegalArgumentException("文件流不能为空");
            }
        }
    }

    /**
     * 将文件保存到调用方指定的逻辑路径。
     *
     * <p>如果目标路径已经存在，存储实现应以新内容替换旧对象；这用于覆盖投标记录级派生的
     * 汇总 Markdown。原始文件路径由上层使用 UUID 保证唯一。</p>
     *
     * @param request 通用文件保存请求
     * @return 存储端确认的文件结果
     * @throws IOException 文件写入或存储属性读取失败
     */
    StoredFile store(StoreRequest request) throws IOException;

    /**
     * 根据存储相对路径（本地）打开文件流。
     *
     * @param relativePath 存储相对路径
     * @return 文件输入流，由调用方关闭
     * @throws IOException 文件不存在或无法读取
     */
    InputStream open(String relativePath) throws IOException;

    /**
     * 按可选 ETag 条件（MinIO）打开文件流。
     *
     * <p>默认实现忽略 ETag 并复用普通读取；对象存储实现可以覆盖该方法执行条件读取。</p>
     *
     * @param request 文件读取请求
     * @return 文件输入流，由调用方关闭
     * @throws IOException 文件不存在、条件不匹配或无法读取
     */
    default InputStream open(OpenRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("文件读取请求不能为空");
        }
        return open(request.relativePath());
    }

    /**
     * 删除指定相对路径的文件。
     *
     * @param relativePath 存储相对路径
     * @throws IOException 文件删除失败
     */
    void delete(String relativePath) throws IOException;
}
