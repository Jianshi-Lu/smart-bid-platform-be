package io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto;

import java.util.List;

/** Python 返回的单文件转换结果。 */
public record PythonFileConvertResponse(
        String originalObjectKey,
        String originalFilename,
        String convertedObjectKey,
        String contentType,
        String status,
        List<String> warnings,
        String error) {
}
