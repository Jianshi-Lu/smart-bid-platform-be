package io.gitee.lujianshi.bidwinningbackend.controller;

import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对外提供统一的服务健康检查入口。
 *
 * <p>该接口复用 Actuator 聚合后的健康状态和 HTTP 状态映射，但只返回状态码，
 * 避免向前端、启动脚本或负载均衡暴露数据库、磁盘等内部诊断详情。</p>
 */
@RestController
public class HealthController {

    private final HealthEndpoint healthEndpoint;
    private final HttpCodeStatusMapper httpCodeStatusMapper;

    public HealthController(
            HealthEndpoint healthEndpoint,
            HttpCodeStatusMapper httpCodeStatusMapper) {
        this.healthEndpoint = healthEndpoint;
        this.httpCodeStatusMapper = httpCodeStatusMapper;
    }

    /**
     * 返回与 Actuator 健康聚合结果一致的精简响应。
     *
     * @return 响应体仅包含 {@code status}；HTTP 状态沿用 Actuator 的状态映射
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        HealthDescriptor descriptor = healthEndpoint.health();
        String status = descriptor.getStatus().getCode();
        int httpStatus = httpCodeStatusMapper.getStatusCode(descriptor.getStatus());
        return ResponseEntity.status(httpStatus).body(Map.of("status", status));
    }
}
