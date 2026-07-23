package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.digest;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件摘要计算能力。
 *
 * <p>输入采用通用 {@link InputStream}，不绑定本地文件、Multipart 上传或对象存储。</p>
 *
 * @since 1.0
 */
public interface FileDigestCalculator {

    /**
     * 返回当前实现使用的摘要算法名称。
     *
     * @return 标准摘要算法名称
     */
    String algorithm();

    /**
     * 读取输入流并返回十六进制摘要。
     *
     * <p>调用方负责关闭输入流。</p>
     *
     * @param input 待计算摘要的文件流
     * @return 小写十六进制摘要
     * @throws IOException 文件流读取失败
     */
    String calculate(InputStream input) throws IOException;
}
