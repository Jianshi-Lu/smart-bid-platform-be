package io.gitee.lujianshi.bidwinningbackend.dto.bidinviteraccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 招标方账号注册请求。
 *
 * <p>与投标方注册请求对称：用户名 + 密码必填，手机号/显示名称可选。
 * 演示环境密码明文存储。</p>
 *
 * @since V39
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "账号名不能为空")
    @Size(max = 64, message = "账号名长度不能超过64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6-64之间")
    private String password;

    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    @Size(max = 100, message = "显示名称长度不能超过100")
    private String displayName;
}
