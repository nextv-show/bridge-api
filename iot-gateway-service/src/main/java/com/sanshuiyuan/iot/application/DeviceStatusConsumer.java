package com.sanshuiyuan.iot.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.iot.domain.DeviceStatus;
import com.sanshuiyuan.iot.infra.repository.DeviceStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 设备状态消费者。UPSERT 维护 device_status；{@code online=false}（含 LWT 遗嘱）时记录 lastLwtAt。
 */
@Service
public class DeviceStatusConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeviceStatusConsumer.class);

    private final DeviceStatusRepository repo;
    private final ObjectMapper objectMapper;
    private final MatchingActivationClient activationClient;

    public DeviceStatusConsumer(DeviceStatusRepository repo, ObjectMapper objectMapper,
                                MatchingActivationClient activationClient) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.activationClient = activationClient;
    }

    public void onStatus(String sn, byte[] payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            boolean online = json.get("online").asBoolean();

            var existing = repo.findBySn(sn);
            boolean wasOnline = existing.map(DeviceStatus::isOnline).orElse(false);

            var status = existing.orElseGet(() -> new DeviceStatus(sn));
            status.setOnline(online);
            status.setLastSeenAt(LocalDateTime.now());
            if (!online) {
                status.setLastLwtAt(LocalDateTime.now());
            }
            repo.save(status);

            log.debug("[MQTT] Device status: sn={}, online={}", sn, online);

            // 029 设备激活：上线边沿（offline/未知 → online）触发，推进 PENDING_ACTIVATE → STAGE_1。
            // 边沿门控天然限频；matching 侧 CAS 幂等（非 PENDING_ACTIVATE 即 no-op），无需本地去重。
            if (online && !wasOnline) {
                activationClient.activate(sn);
            }
        } catch (Exception e) {
            log.error("[MQTT] Failed to process status from sn={}: {}", sn, e.getMessage());
        }
    }
}
