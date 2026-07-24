package io.gitee.lujianshi.bidwinningbackend.credentialfile.enums;

/** 资信文件存储实现类型。 */
public enum FileStorageType {
    /** 使用当前 Java NIO 本地文件系统。 */
    LOCAL,

    /** 使用 MinIO 对象存储服务。 */
    MINIO
}
