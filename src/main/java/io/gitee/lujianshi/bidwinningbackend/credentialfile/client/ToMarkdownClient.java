package io.gitee.lujianshi.bidwinningbackend.credentialfile.client;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileConverterProperties;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Java 调用 to-markdown 的唯一 HTTP 客户端。
 *
 * <p>客户端只负责把 Java 已读取的原文件流包装为 multipart 请求，并校验 Python
 * 返回的 Markdown 文件协议；对象存储读取、目标路径生成和 Markdown 持久化由业务层负责。</p>
 */
@Component
public class ToMarkdownClient {

    private static final MediaType MARKDOWN_MEDIA_TYPE =
            MediaType.parseMediaType("text/markdown");

    private final RestClient restClient;

    public ToMarkdownClient(
            RestClient.Builder restClientBuilder,
            FileConverterProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /**
     * 将调用方提供的原文件流发送给 Python，并返回已校验的 Markdown 文件。
     *
     * @param inputStream 原文件内容流；请求完成前必须保持可读
     * @param filename 安全原始文件名，仅用于 multipart 文件名和格式识别
     * @param contentType 原文件 MIME 类型，未知或不合法时使用二进制流类型
     * @param contentLength 原文件字节数
     * @return Python 返回的 Markdown 文件
     * @throws AppException Python 调用失败或响应协议不合法
     */
    public PythonMarkdownResponse convert(
            InputStream inputStream,
            String filename,
            String contentType,
            long contentLength) {
        validateRequest(inputStream, filename, contentLength);

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(parseContentType(contentType));
        partHeaders.setContentDisposition(
                ContentDisposition.formData()
                        .name("file")
                        .filename(filename, StandardCharsets.UTF_8)
                        .build());
        HttpEntity<InputStreamResource> filePart = new HttpEntity<>(
                new KnownLengthInputStreamResource(
                        inputStream, filename, contentLength),
                partHeaders);
        MultiValueMap<String, Object> multipartBody =
                new LinkedMultiValueMap<>();
        multipartBody.add("file", filePart);

        try {
            ResponseEntity<byte[]> response = restClient.post()
                    .uri("/convert")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MARKDOWN_MEDIA_TYPE)
                    .body(multipartBody)
                    .retrieve()
                    .toEntity(byte[].class);
            return validateResponse(response);
        } catch (RestClientException e) {
            throw conversionFailure(
                    "PYTHON_CALL", "资信文件转换服务调用失败", e);
        }
    }

    /** 校验发送 multipart 所需的最小文件元数据。 */
    private void validateRequest(
            InputStream inputStream,
            String filename,
            long contentLength) {
        if (inputStream == null) {
            throw conversionFailure(
                    "BUILD_PYTHON_REQUEST", "待转换文件流不能为空", null);
        }
        if (filename == null || filename.isBlank()) {
            throw conversionFailure(
                    "BUILD_PYTHON_REQUEST", "待转换文件名不能为空", null);
        }
        if (contentLength <= 0) {
            throw conversionFailure(
                    "BUILD_PYTHON_REQUEST", "待转换文件大小不正确", null);
        }
    }

    /** MIME 类型不可信时回退为通用二进制类型，避免构造非法 multipart 请求。 */
    private MediaType parseContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /** 校验 Python 成功响应必须是非空的单个 Markdown 文件。 */
    private PythonMarkdownResponse validateResponse(
            ResponseEntity<byte[]> response) {
        if (response == null) {
            throw conversionFailure(
                    "PYTHON_RESPONSE", "资信文件转换服务返回为空", null);
        }

        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null
                || !MARKDOWN_MEDIA_TYPE.isCompatibleWith(contentType)) {
            throw conversionFailure(
                    "VALIDATE_PYTHON_RESPONSE",
                    "资信文件转换服务未返回 Markdown 文件",
                    null);
        }

        byte[] markdown = response.getBody();
        if (markdown == null || markdown.length == 0) {
            throw conversionFailure(
                    "VALIDATE_PYTHON_RESPONSE",
                    "资信文件转换服务返回的 Markdown 为空",
                    null);
        }
        long declaredLength = response.getHeaders().getContentLength();
        if (declaredLength >= 0 && declaredLength != markdown.length) {
            throw conversionFailure(
                    "VALIDATE_PYTHON_RESPONSE",
                    "资信文件转换响应长度不一致",
                    null);
        }

        String responseFilename =
                response.getHeaders().getContentDisposition().getFilename();
        if (responseFilename == null
                || !responseFilename.toLowerCase().endsWith(".md")) {
            throw conversionFailure(
                    "VALIDATE_PYTHON_RESPONSE",
                    "资信文件转换响应缺少 Markdown 文件名",
                    null);
        }
        return new PythonMarkdownResponse(
                markdown,
                responseFilename,
                contentType.toString());
    }

    /** 创建统一的 Python 转换调用异常。 */
    private AppException conversionFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_CONVERSION_FAILED,
                "credential-file",
                "CONVERT",
                stage,
                message,
                cause);
    }

    /** 提供 multipart 编码所需文件名和已知长度，但不接管输入流生命周期。 */
    private static final class KnownLengthInputStreamResource
            extends InputStreamResource {

        private final String filename;
        private final long contentLength;

        private KnownLengthInputStreamResource(
                InputStream inputStream,
                String filename,
                long contentLength) {
            super(inputStream);
            this.filename = filename;
            this.contentLength = contentLength;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }
    }
}
