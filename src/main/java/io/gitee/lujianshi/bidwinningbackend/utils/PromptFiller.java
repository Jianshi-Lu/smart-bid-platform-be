package io.gitee.lujianshi.bidwinningbackend.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Prompt 模板填充工具。
 *
 * <p>负责从 classpath 加载 prompt 模板文件（{@code resources/prompts/*.txt}），
 * 并将变量占位符替换为实际值后返回填充好的 prompt 字符串。</p>
 *
 * <h3>占位符格式</h3>
 * <pre>
 * &#123;&#123;supplierName&#125;&#125;   — 供应商名称
 * &#123;&#123;quotePrice&#125;&#125;    — 报价
 * &#123;&#123;supplierContact&#125;&#125; — 联系方式
 * &#123;&#123;background&#125;&#125;    — 供应商背景信息（由 BidBackgroundLoader 拼装）
 * </pre>
 *
 * <h3>渲染方式</h3>
 * <p>使用简单的 {@code String.replace()} 逐个替换占位符。</p>
 *
 * <h3>API 选择</h3>
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
     * 填充 prompt 模板中的变量占位符。
     *
     * @param template 原始模板字符串（含 &#123;&#123;varName&#125;&#125; 占位符）
     * @param vars     变量名 → 变量值的映射
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
     * 从 classpath 加载模板文件（不填充变量）。
     *
     * <p>用于无占位符的静态模板（如 system prompt），比 {@link #fillFromResource}
     * 节省无意义的 String.replace 迭代。</p>
     *
     * @param templatePath classpath 下的模板路径，如 {@code prompts/risk-analysis-system-v1.txt}
     * @return 模板原文；ResourceLoader 未配置或读取失败时返回空字符串
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
     * 加载并填充 prompt 模板。
     *
     * @param templatePath classpath 下的模板路径，如 {@code prompts/risk-analysis-user-v1.txt}
     * @param vars         变量名 → 变量值的映射
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