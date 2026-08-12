package io.gitee.lujianshi.bidwinningbackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ProtectedEndpointConfigTest {

    @Test
    void mailSendingEndpointsAreCoveredByInviterAuthentication() {
        WebMvcConfig config = new WebMvcConfig();
        ReflectionTestUtils.setField(
                config,
                "bidInviterAuthInterceptor",
                mock(BidInviterAuthInterceptor.class));
        InterceptorRegistry registry = new InterceptorRegistry();

        config.addInterceptors(registry);

        List<?> interceptors = ReflectionTestUtils.invokeMethod(
                registry, "getInterceptors");
        MappedInterceptor mapped = (MappedInterceptor) interceptors.getFirst();
        List<String> includes = Arrays.asList(mapped.getIncludePathPatterns());
        assertTrue(includes.contains("/send-email"));
        assertTrue(includes.contains("/send-notification"));
    }
}
