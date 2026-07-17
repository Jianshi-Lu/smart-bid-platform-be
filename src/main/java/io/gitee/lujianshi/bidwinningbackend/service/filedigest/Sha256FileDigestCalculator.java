package io.gitee.lujianshi.bidwinningbackend.service.filedigest;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 基于 JDK {@link MessageDigest} 的 SHA-256 文件摘要实现。
 *
 * @since 1.0
 */
@Component("sha256FileDigestCalculator")
public class Sha256FileDigestCalculator implements FileDigestCalculator {

    private static final String ALGORITHM = "SHA-256";

    private static final int BUFFER_SIZE = 8192;

    @Override
    public String algorithm() {
        return ALGORITHM;
    }

    @Override
    public String calculate(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("文件流不能为空");
        }

        MessageDigest digest = createDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", e);
        }
    }
}
