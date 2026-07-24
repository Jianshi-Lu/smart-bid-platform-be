package io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto;

/** 调用 Python 单文件 MinIO 转换接口的请求体。 */
public record PythonFileConvertRequest(String originalObjectKey) {
}
