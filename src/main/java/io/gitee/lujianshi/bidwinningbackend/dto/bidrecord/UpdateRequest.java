package io.gitee.lujianshi.bidwinningbackend.dto.bidrecord;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新投标记录请求 DTO（PATCH 语义，null 字段表示"不修改"）。
 *
 * <p>PK（bidRecordId）/ 外键（invitationId）/ 投标次数（bidNo）均不开放修改，
 * 服务端确保 bid_no 仅按 (invitationId, bidderName) 服务端生成。</p>
 */
@Data
public class UpdateRequest {

    @Size(max = 100, message = "投标人名称长度不能超过100")
    @Pattern(regexp = ".*\\S.*", message = "投标人名称不能为空")
    private String bidderName;

    private BigDecimal quotePrice;

    @Size(max = 255, message = "投标人联系方式长度不能超过255")
    private String bidderContact;

    private List<String> credentials;

    /** credentials 是只读派生投影，不能单独构成一次有效更新。 */
    @JsonIgnore
    @AssertTrue(message = "至少提供一个可更新字段")
    public boolean isUpdatePresent() {
        return bidderName != null
                || quotePrice != null
                || bidderContact != null;
    }
}
