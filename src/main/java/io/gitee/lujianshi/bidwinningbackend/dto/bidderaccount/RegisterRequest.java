package io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 投标方账号注册请求。
 *
 * <p>最小身份模型：用户名 + 密码必填，手机号/显示名称可选。
 * 密码由服务端使用 PBKDF2 哈希后入库，不保存明文。</p>
 *
 * @since V36
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
