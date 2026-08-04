package io.gitee.lujianshi.bidwinningbackend.knowledgefile.key;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** 为每个知识文件生成贯穿对象存储和向量库的稳定 UUID。 */
@Component
public class DocumentUuidGenerator {

    public UUID generate() {
        return UUID.randomUUID();
    }
}
