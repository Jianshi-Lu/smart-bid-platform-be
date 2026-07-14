package io.gitee.lujianshi.bidwinningbackend.dto.bidrecord;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建投标记录请求 DTO。
 *
 * <p>必填字段：invitationId（招标公告）/ bidderName（投标人）/ quotePrice（报价）/ bidderContact（联系方式）。
 * {@code bidNo} 由服务端按 (invitationId, bidderName) 计算 max(bid_no)+1，无需客户端传。
 * {@code credentials}（转换后 Markdown 文件路径列表）可选，按文件顺序传入。</p>
 */
@Data
public class AddRequest {

    @NotNull(message = "招标公告ID不能为空")
    private Integer invitationId;

    @NotBlank(message = "投标人名称不能为空")
    @Size(max = 100, message = "投标人名称长度不能超过100")
    private String bidderName;

    @NotNull(message = "报价不能为空")
    private BigDecimal quotePrice;

    @NotBlank(message = "投标人联系方式不能为空")
    @Size(max = 255, message = "投标人联系方式长度不能超过255")
    private String bidderContact;

    private List<String> credentials;
}
