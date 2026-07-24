package io.gitee.lujianshi.bidwinningbackend.credentialfile.enums;

/** 资信材料在批次和文件两个层级共用的转换状态。 */
public enum ConvertStatus {
    /** 批次仍在接收文件；文件层级通常不会停留在该状态。 */
    UPLOADING,

    /** 原始文件已经全部落盘，等待转换。 */
    UPLOADED,

    /** 正在执行 Markdown 转换。 */
    CONVERTING,

    /** 转换已经完成。 */
    CONVERTED,

    /** 上传或转换失败。 */
    FAILED
}
