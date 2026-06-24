package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import org.springframework.stereotype.Component;

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
 * &#123;&#123;amount&#125;&#125;        — 中标金额
 * &#123;&#123;projectName&#125;&#125;   — 项目名称
 * &#123;&#123;handlerName&#125;&#125;   — 经办人姓名
 * &#123;&#123;background&#125;&#125;    — 供应商背景信息（由 BidBackgroundLoader 拼装）
 * </pre>
 *
 * <h3>渲染方式</h3>
 * <p>当前版本使用简单的 {@code String.replace()} 逐个替换占位符。
 * 未来可升级为 FreeMarker 或 Thymeleaf（项目已有 Thymeleaf 依赖）。</p>
 *
 * @see BidBackgroundLoader
 */
@Component
public class PromptFiller {

    /**
     * 填充 prompt 模板中的变量占位符。
     *
     * @param template 原始模板字符串（含 &#123;&#123;varName&#125;&#125; 占位符）
     * @param vars     变量名 → 变量值的映射
     * @return 填充后的字符串（占位符无对应值时保持原样）
     */
    public String fill(String template, Map<String, String> vars) {
        // TODO: 实现变量替换逻辑
        // 当前版本直接返回原模板
        return template;
    }

    /**
     * 加载并填充 prompt 模板。
     *
     * @param templatePath classpath 下的模板路径，如 {@code prompts/risk-analysis-user-v1.txt}
     * @param vars         变量名 → 变量值的映射
     * @return 填充后的字符串
     */
    public String fillFromResource(String templatePath, Map<String, String> vars) {
        // TODO: 1. ResourceLoader 加载 classpath 模板文件
        // TODO: 2. 读取为 String
        // TODO: 3. 调用 fill() 填充变量
        return "";
    }
}
