package io.gitee.lujianshi.bidwinningbackend.dto.bidinviteraccount;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 招标方账号登录请求。
 *
 * @since V39
 */
@Data
public class LoginRequest {

    @NotBlank(message = "账号名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
