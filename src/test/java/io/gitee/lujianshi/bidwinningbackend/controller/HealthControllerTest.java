package io.gitee.lujianshi.bidwinningbackend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.contributor.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "management.health.defaults.enabled=false")
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private HealthController controller;

    @Autowired
    private HttpCodeStatusMapper statusMapper;

    @Test
    void shouldExposeActuatorStatusWithSameHttpMapping() {
        ResponseEntity<Map<String, String>> response = controller.health();
        String status = response.getBody().get("status");

        assertEquals(Map.of("status", status), response.getBody());
        assertEquals(statusMapper.getStatusCode(new Status(status)),
                response.getStatusCode().value());
    }
}
