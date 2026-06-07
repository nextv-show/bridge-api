package com.sanshuiyuan.iot.application;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.function.BiConsumer;

/**
 * MQTT 订阅编排：启动时订阅设备遥测/状态/告警主题，按 topic 拆分 SN 后派发到各消费者。
 * 主题约定：{@code device/{sn}/...}，SN 取第二段。
 */
@Service
public class MqttSubscriberService implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriberService.class);

    private final Mqtt5AsyncClient mqttClient;
    private final TelemetryFlowConsumer flowConsumer;
    private final TelemetryTdsConsumer tdsConsumer;
    private final TelemetryFilterConsumer filterConsumer;
    private final DeviceStatusConsumer statusConsumer;
    private final AlarmConsumer alarmConsumer;
    private final StopEventConsumer stopEventConsumer;

    public MqttSubscriberService(Mqtt5AsyncClient mqttClient, TelemetryFlowConsumer flowConsumer,
                                 TelemetryTdsConsumer tdsConsumer, TelemetryFilterConsumer filterConsumer,
                                 DeviceStatusConsumer statusConsumer, AlarmConsumer alarmConsumer,
                                 StopEventConsumer stopEventConsumer) {
        this.mqttClient = mqttClient;
        this.flowConsumer = flowConsumer;
        this.tdsConsumer = tdsConsumer;
        this.filterConsumer = filterConsumer;
        this.statusConsumer = statusConsumer;
        this.alarmConsumer = alarmConsumer;
        this.stopEventConsumer = stopEventConsumer;
    }

    @Override
    public void afterPropertiesSet() {
        subscribe("device/+/telemetry/flow", (topic, payload) -> {
            String sn = extractSn(topic);
            flowConsumer.onFlow(sn, payload);
        });
        subscribe("device/+/telemetry/tds", (topic, payload) -> {
            String sn = extractSn(topic);
            tdsConsumer.onTds(sn, payload);
        });
        subscribe("device/+/telemetry/filter", (topic, payload) -> {
            String sn = extractSn(topic);
            filterConsumer.onFilter(sn, payload);
        });
        subscribe("device/+/status", (topic, payload) -> {
            String sn = extractSn(topic);
            statusConsumer.onStatus(sn, payload);
        });
        subscribe("device/+/event/alarm", (topic, payload) -> {
            String sn = extractSn(topic);
            alarmConsumer.onAlarm(sn, payload);
        });
        subscribe("device/+/event/stop", (topic, payload) -> {
            String sn = extractSn(topic);
            stopEventConsumer.onStop(sn, payload);
        });
        log.info("[MQTT] Subscribed to device topics");
    }

    private void subscribe(String topicFilter, BiConsumer<String, byte[]> handler) {
        mqttClient.subscribeWith()
                .topicFilter(topicFilter)
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(p -> handler.accept(p.getTopic().toString(), p.getPayloadAsBytes()))
                .send();
    }

    private String extractSn(String topic) {
        // device/{sn}/... → sn
        String[] parts = topic.split("/");
        return parts.length > 1 ? parts[1] : "unknown";
    }

    @Override
    public void destroy() {
        mqttClient.disconnect();
    }
}
