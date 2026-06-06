package com.sanshuiyuan.iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 三水元 IoT 网关服务（iot-gateway-service，003）。
 * 业务主体：MQTT 接入/设备认证/遥测解析（003 plan）。设备认证走 MQTT，不走 HTTP；
 * 连 h5_db（独立 flyway 历史表）；端口 8089。
 */
@SpringBootApplication
public class IotGatewayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IotGatewayServiceApplication.class, args);
    }
}
