package io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件校验和计算能力。
 *
 * <p>输入采用通用 {@link InputStream}，不绑定 multipart、本地文件或对象存储。</p>
 *
 * @since 1.0
 */
public interface FileChecksumCalculator {

    /**
     * 返回当前实现使用的校验和算法名称。
     *
     * @return 标准算法名称
     */
    String algorithm();

    /**
     * 读取输入流并返回十六进制校验和。
     *
     * <p>调用方负责关闭输入流。</p>
     *
     * @param input 待计算校验和的文件流
     * @return 小写十六进制校验和
     * @throws IOException 文件流读取失败
     */
    String calculate(InputStream input) throws IOException;
}
