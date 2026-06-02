package com.sanshuiyuan.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 三水元 物流工单服务（logistics-service，002）。消费 logistics_outbox 建工单，状态机
 * PENDING_SHIP→SHIPPED→DELIVERED→INSTALLED；连 h5_db（E-2）；端口 8087（E-3）。
 * 鉴权/webhook 签名在 Phase D 补；本脚手架仅 actuator 放行。
 */
@SpringBootApplication
@EnableScheduling
public class LogisticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogisticsServiceApplication.class, args);
    }
}
