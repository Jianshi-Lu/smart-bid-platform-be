package io.gitee.lujianshi.bidwinningbackend.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：为招标方管理接口注册统一登录鉴权拦截器。
 *
 * @since V39
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private BidInviterAuthInterceptor bidInviterAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(bidInviterAuthInterceptor)
                .addPathPatterns(
                        "/invitation-for-bid/**",
                        "/bid-handler-info/**",
                        "/bid-record/**",
                        "/risk-analysis/**",
                        "/chat-agent/**",
                        "/knowledge-files/**",
                        "/credential-files/**")
                .excludePathPatterns(
                        "/bid-inviter/auth/**",
                        "/service-status/**",
                        "/actuator/**",
                        "/error");
    }
}
