package com.sanshuiyuan.iot;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.sanshuiyuan.iot.application.MqttSubscriberService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 上下文冒烟测试：用 Testcontainers 起真实 MySQL 跑 Flyway 迁移（V040~V046 建表），
 * ddl-auto=validate 校验实体。MQTT broker 测试环境不可用，故 mock 客户端与订阅服务，
 * 避免 MqttConfig 在 bean 初始化时真实连接 broker 导致上下文启动失败。
 */
@SpringBootTest
class IotGatewayServiceApplicationTests {

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("core_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        if (!MYSQL.isRunning()) {
            MYSQL.start();
        }
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.locations", () -> "classpath:db/migration");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    // MQTT broker 测试不可用：mock 掉客户端与订阅服务，避免启动时真实连接
    @MockBean
    Mqtt5BlockingClient mqttBlockingClient;
    @MockBean
    Mqtt5AsyncClient mqttAsyncClient;
    @MockBean
    MqttSubscriberService mqttSubscriberService;

    @Test
    void contextLoads() {
    }
}
