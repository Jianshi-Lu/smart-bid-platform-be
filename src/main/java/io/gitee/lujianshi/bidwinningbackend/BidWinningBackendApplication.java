package io.gitee.lujianshi.bidwinningbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController

public class BidWinningBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BidWinningBackendApplication.class, args);
    }

    // 访问地址：http://localhost:8080/hello
    @GetMapping("/hello")
    public String hello() {
        return "Hello World!";
    }
}
