package com.sanshuiyuan.iot.config;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * MQTT v5 客户端配置（HiveMQ client）。提供阻塞与异步两套客户端：
 * 异步客户端用于订阅遥测/状态/告警主题，阻塞/异步均可用于下发命令。
 * 同时开启 {@link EnableScheduling} 供告警阈值定时评估使用。
 */
@Configuration
@EnableScheduling
public class MqttConfig {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.client-id-prefix:iot-gw-}")
    private String clientIdPrefix;

    @Bean
    public Mqtt5BlockingClient mqttBlockingClient() {
        String clientId = clientIdPrefix + UUID.randomUUID().toString().substring(0, 8);
        Mqtt5BlockingClient client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(MqttClientIdentifier.of(clientId))
                .serverHost(extractHost(brokerUrl))
                .serverPort(extractPort(brokerUrl))
                .buildBlocking();
        client.connectWith()
                .simpleAuth()
                    .username(username)
                    .password(password.getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                .send();
        return client;
    }

    @Bean
    public Mqtt5AsyncClient mqttAsyncClient() {
        String clientId = clientIdPrefix + "async-" + UUID.randomUUID().toString().substring(0, 8);
        Mqtt5AsyncClient client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(MqttClientIdentifier.of(clientId))
                .serverHost(extractHost(brokerUrl))
                .serverPort(extractPort(brokerUrl))
                .buildAsync();
        client.connectWith()
                .simpleAuth()
                    .username(username)
                    .password(password.getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                .send();
        return client;
    }

    private String extractHost(String url) {
        // tcp://host:port → host
        String stripped = url.replace("tcp://", "").replace("ssl://", "");
        return stripped.split(":")[0];
    }

    private int extractPort(String url) {
        String stripped = url.replace("tcp://", "").replace("ssl://", "");
        String[] parts = stripped.split(":");
        return parts.length > 1 ? Integer.parseInt(parts[1]) : 1883;
    }
}
