package io.gitee.lujianshi.bidwinningbackend.enums;

/**
 * 资信文件 Markdown 转换状态。
 *
 * <p>数据库中按枚举名称写入 {@code VARCHAR(20)}，避免使用未约束的任意字符串。</p>
 *
 * @since V21
 */
public enum CredentialFileConvertStatus {

    /** 原始文件已保存，等待转换。 */
    UPLOADED,

    /** 正在调用 Python 服务进行 Markdown 转换。 */
    CONVERTING,

    /** Markdown 文件已生成并保存。 */
    CONVERTED,

    /** Markdown 转换失败。 */
    FAILED
}
