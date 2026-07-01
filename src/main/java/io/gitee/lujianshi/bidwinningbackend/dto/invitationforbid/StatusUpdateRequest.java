package io.gitee.lujianshi.bidwinningbackend.dto.invitationforbid;

import io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 招标公告状态变更请求 DTO。三个字段全部通过 RequestBody 传入。
 */
@Data
public class StatusUpdateRequest {

    @NotNull(message = "公告ID不能为空")
    private Integer invitationId;

    @NotNull(message = "状态不能为空")
    private BidNoticeStatus status;

    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
}
