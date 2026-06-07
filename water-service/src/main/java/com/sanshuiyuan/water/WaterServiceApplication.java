package com.sanshuiyuan.water;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 三水元 用水服务（water-service，003）。
 * 业务主体：钱包/充值/出水/流水（003 plan）。鉴权用 h5-service H5 JWT；
 * 连 core_db（与 users/device_assets 同库，独立 flyway 历史表）；端口 8088。
 */
@SpringBootApplication
public class WaterServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaterServiceApplication.class, args);
    }
}
