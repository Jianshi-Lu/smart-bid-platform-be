package io.gitee.lujianshi.bidwinningbackend.dto.credentialfile;

import io.gitee.lujianshi.bidwinningbackend.enums.CredentialFileConvertStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个资信文件上传结果。
 *
 * <p>{@code originalFilePath} 为相对于存储根目录的路径，不暴露具体运行环境的绝对路径。</p>
 *
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CredentialFileUploadItem {

    private Integer fileOrder;

    private String originalFilename;

    private String contentType;

    private Long fileSize;

    private String sha256;

    private String originalFilePath;

    private CredentialFileConvertStatus convertStatus;
}
