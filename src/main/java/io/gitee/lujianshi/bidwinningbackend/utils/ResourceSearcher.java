package io.gitee.lujianshi.bidwinningbackend.utils;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;

/**
 * classpath 模板文件存在性校验。扫描 {@code classpath:/templates/*.html}，按 templateCode（不含 .html 后缀）精确匹配。
 */
@Service
public class ResourceSearcher {

    @jakarta.annotation.Resource
    private ResourcePatternResolver resourcePatternResolver;

    /**
     * 验证模板是否存在。
     *
     * @param templateCode 模板代码（不含 .html 后缀）
     * @throws IllegalArgumentException 模板不存在或搜索失败
     */
    public void validateTemplateExists(String templateCode) {
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:/templates/*.html");
            boolean exists = Arrays.stream(resources)
                    .map(this::extractTemplateName)
                    .anyMatch(name -> name != null && name.equals(templateCode));
            if (!exists) {
                throw new IllegalArgumentException("模板不存在: " + templateCode);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("模板搜索失败: " + templateCode, e);
        } finally {
            System.out.println("模板搜索完成: " + templateCode);
        }
    }

    private String extractTemplateName(Resource resource) {
        try {
            String path = resource.getURL().getPath();
            int idx = path.lastIndexOf("/templates/");
            if (idx < 0) return null;
            return path.substring(idx + "/templates/".length()).replace(".html", "");
        } catch (IOException e) {
            return null;
        }
    }
}
