package io.gitee.lujianshi.bidwinningbackend.credentialfile.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 资信文件 multipart 上传请求。
 *
 * <p>前端以重复的 {@code files} 字段一次性提交当前资信材料批次的全部文件。
 * 批次 UUID 由 Java 后端生成，不由请求方传入。</p>
 *
 * @since 1.0
 */
@Data
@NoArgsConstructor
public class UploadRequest {

    @NotNull(message = "投标记录 ID 不能为空")
    @Positive(message = "投标记录 ID 不正确")
    private Integer bidRecordId;

    @NotEmpty(message = "上传文件不能为空")
    private List<MultipartFile> files;
}
