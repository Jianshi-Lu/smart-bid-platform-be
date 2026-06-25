package io.gitee.lujianshi.bidwinningbackend.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Prompt 模板填充工具。从 classpath 加载模板并替换 {{var}} 占位符。
 *
 * <p>提供两种加载方式：</p>
 * <ul>
 *   <li>{@link #loadFromResource(String)} — 用于无占位符的静态模板（如 system prompt）</li>
 *   <li>{@link #fillFromResource(String, Map)} — 用于含占位符的动态模板（如 user prompt）</li>
 * </ul>
 */
@Component
public class PromptFiller {

    @Autowired(required = false)
    private ResourceLoader resourceLoader;

    /**
     * 替换模板中的 {{key}} 占位符。
     *
     * @param template 原始模板
     * @param vars     变量名 → 值
     * @return 填充后的字符串（占位符无对应值时保持原样）
     */
    public String fill(String template, Map<String, String> vars) {
        if (template == null || vars == null || vars.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * 从 classpath 加载模板（不填充）。用于无占位符的静态模板。
     */
    public String loadFromResource(String templatePath) {
        if (resourceLoader == null) return "";
        try {
            Resource resource = resourceLoader.getResource("classpath:" + templatePath);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 加载模板并填充占位符。
     *
     * @param templatePath classpath 下的模板路径
     * @param vars         变量名 → 值
     * @return 填充后的字符串
     */
    public String fillFromResource(String templatePath, Map<String, String> vars) {
        if (resourceLoader == null) {
            return fill("", vars);
        }
        try {
            Resource resource = resourceLoader.getResource("classpath:" + templatePath);
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return fill(template, vars);
        } catch (IOException e) {
            return fill("", vars);
        }
    }
}
