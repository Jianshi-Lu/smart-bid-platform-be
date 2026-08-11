package io.gitee.lujianshi.bidwinningbackend.credentialfile.dto;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 资信文件上传响应。
 *
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    private List<UploadedFile> files;

    /**
     * 单个已上传文件的响应信息。
     *
     * <p>{@code originalFilePath} 是存储逻辑相对路径，不暴露运行环境的绝对路径。</p>
     *
     * @since 1.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadedFile {

        private Long fileId;

        private Integer fileOrder;

        private String originalFilename;

        private String contentType;

        private Long fileSize;

        private String originalSha256;

        private String originalFilePath;

        private String originalStorageStatus;

        private ConvertStatus convertStatus;

        private String indexStatus;

        private Integer chunkCount;

        private LocalDateTime indexedAt;

        private String errorMessage;

        /** 失败阶段：UPLOAD/CONVERT/UNKNOWN（对齐 knowledge_file）。 */
        private String errorStage;
    }
}
