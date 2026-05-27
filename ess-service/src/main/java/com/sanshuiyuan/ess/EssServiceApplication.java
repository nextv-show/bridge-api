package com.sanshuiyuan.ess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRetry
@EnableAsync
@EnableScheduling
public class EssServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EssServiceApplication.class, args);
    }
}
