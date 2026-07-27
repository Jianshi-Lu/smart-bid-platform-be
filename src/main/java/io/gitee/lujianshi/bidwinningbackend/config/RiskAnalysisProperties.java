package io.gitee.lujianshi.bidwinningbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 风险分析配置。 */
@ConfigurationProperties(prefix = "risk-analysis")
@Getter
@Setter
public class RiskAnalysisProperties {

    /**
     * 汇总 Markdown 的最大字符数。
     *
     * <p>当前阶段不引入模型 tokenizer，使用字符数作为上下文长度的近似控制；默认约为
     * 128K 字符。</p>
     */
    private int maxMergedMarkdownChars = 131_072;
}
