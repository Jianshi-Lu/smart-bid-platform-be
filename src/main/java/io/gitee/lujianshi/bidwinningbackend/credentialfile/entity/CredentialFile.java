package io.gitee.lujianshi.bidwinningbackend.credentialfile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资信文件实体。对应表 {@code credential_file}（V21/V23）。
 *
 * <p>每个原始文件对应一条记录；同一次上传的文件通过 {@code fileUuid} 聚合。
 * {@code bidRecordId} 在创建投标记录后回填，转换前为 {@code null} 的 Markdown 路径也保持为 {@code null}。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("credential_file")
public class CredentialFile {

    @TableId(type = IdType.AUTO)
    private Long fileId;

    private String fileUuid;

    private Integer bidRecordId;

    private Integer fileOrder;

    private String originalFilename;

    private String contentType;

    private Long fileSize;

    private String sha256;

    private String storageType;

    private String originalFilePath;

    private String markdownFilePath;

    private ConvertStatus status;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime convertedAt;

    private LocalDateTime boundAt;
}
