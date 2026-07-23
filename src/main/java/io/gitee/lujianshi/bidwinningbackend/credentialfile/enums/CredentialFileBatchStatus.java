package io.gitee.lujianshi.bidwinningbackend.credentialfile.enums;

/**
 * 资信文件上传批次状态。
 *
 * <p>批次状态描述整批原始文件的生命周期；单个文件的 Markdown 转换状态由
 * {@link CredentialFileConvertStatus} 单独描述。</p>
 *
 * @since V22
 */
public enum CredentialFileBatchStatus {

    /** 批次已经创建，原始文件仍在上传和校验中。 */
    UPLOADING,

    /** 本批次全部原始文件已经上传并通过完整性校验。 */
    UPLOADED,

    /** 正在执行本批次 Markdown 转换。 */
    CONVERTING,

    /** 本批次全部文件已经完成 Markdown 转换。 */
    CONVERTED,

    /** 本批次上传或转换失败。 */
    FAILED
}
