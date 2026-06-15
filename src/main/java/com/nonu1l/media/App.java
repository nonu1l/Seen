package com.nonu1l.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口，加载 Spring Boot 上下文并开启异步与定时任务能力。
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class App {

    /**
     * 程序主入口。
     *
     * @param args 启动参数，通常用于覆盖 Spring 配置或环境变量。
     */
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
