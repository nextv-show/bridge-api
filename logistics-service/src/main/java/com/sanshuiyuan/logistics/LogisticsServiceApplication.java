package com.sanshuiyuan.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 三水元 物流工单服务（logistics-service，002）。消费 logistics_outbox 建工单，状态机
 * PENDING_SHIP→SHIPPED→DELIVERED→INSTALLED；连 core_db（E-2）；端口 8087（E-3）。
 */
@SpringBootApplication
@EnableScheduling
@EnableRetry
public class LogisticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogisticsServiceApplication.class, args);
    }
}
