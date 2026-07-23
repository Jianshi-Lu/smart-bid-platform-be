package io.gitee.lujianshi.bidwinningbackend.credentialfile.intake;

import java.io.IOException;
import java.io.InputStream;

/**
 * 上传条目的文件内容入口。
 *
 * <p>每次调用 {@link #openStream()} 都必须返回一个新的、可独立读取和关闭的输入流。
 * 该约束允许处理流程先计算校验和，再重新打开同一上传内容执行持久化，而无需将整个文件
 * 读入 Java 堆内存。</p>
 *
 * @since 1.0
 */
@FunctionalInterface
public interface ItemContent {

    /**
     * 打开一条新的上传内容输入流。
     *
     * @return 可独立读取和关闭的输入流
     * @throws IOException 上传内容无法读取
     */
    InputStream openStream() throws IOException;
}
