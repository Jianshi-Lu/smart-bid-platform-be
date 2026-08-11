package io.gitee.lujianshi.bidwinningbackend.credentialfile.enums;

/** 资信文件转换状态。 */
public enum ConvertStatus {
    /** 保留的兼容状态；当前单文件上传链路通常从 UPLOADED 开始。 */
    UPLOADING,

    /** 原始文件已完成存储校验，等待转换。 */
    UPLOADED,

    /** 正在执行 Markdown 转换。 */
    CONVERTING,

    /** 转换已经完成。 */
    CONVERTED,

    /** 上传或转换失败。 */
    FAILED
}
