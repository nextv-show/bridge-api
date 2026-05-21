package com.sanshuiyuan.h5;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 三水元 微信 H5 服务（h5-service）。
 * 承载 P1 落地页配置只读接口（102），后续 103/104/105 复用本模块脚手架（common/ + config/）。
 * 端口 8083（user 8081 / asset 8082 之后顺延）。
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class H5ServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(H5ServiceApplication.class, args);
    }
}
