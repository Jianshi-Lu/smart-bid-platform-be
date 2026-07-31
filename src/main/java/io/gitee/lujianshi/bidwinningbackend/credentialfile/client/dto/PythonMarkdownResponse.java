package io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto;

/**
 * Python 单文件转换接口返回的 Markdown 文件。
 *
 * @param markdown UTF-8 Markdown 文件正文
 * @param filename Python 响应头声明的 Markdown 文件名
 * @param contentType Python 响应的完整 Content-Type
 */
public record PythonMarkdownResponse(
        byte[] markdown,
        String filename,
        String contentType) {

    /** 复制可变字节数组，避免客户端响应在业务层之间被意外改写。 */
    public PythonMarkdownResponse {
        markdown = markdown == null ? null : markdown.clone();
    }

    /** 返回 Markdown 正文副本。 */
    @Override
    public byte[] markdown() {
        return markdown == null ? null : markdown.clone();
    }
}
