package com.sanshuiyuan.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 三水元 撮合服务（matching-service，002）。
 * 撮合主体：所有权人小程序自助接单（plan §0.5 E-4）。鉴权用 h5-service H5 JWT → h5_db.users.id（E-1）；
 * 连 h5_db（E-2，与 users/device_assets 同库）；端口 8086（E-3）。
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class MatchingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MatchingServiceApplication.class, args);
    }
}
