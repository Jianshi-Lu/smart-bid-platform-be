package io.gitee.lujianshi.bidwinningbackend.dto.credentialfile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 资信文件批次上传结果。
 *
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CredentialFileUploadResponse {

    private String uploadBatchId;

    private List<CredentialFileUploadItem> files;
}
