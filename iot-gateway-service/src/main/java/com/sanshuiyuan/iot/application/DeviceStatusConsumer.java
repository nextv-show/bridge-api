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

    /**
     * 已成功送达 matching 的激活请求（HTTP 2xx，含 already-activated）。进程内 best-effort：
     * 仅用于在调用成功后停止重试；重启丢失无害（下次在线心跳再调一次，matching 幂等）。
     */
    private final java.util.Set<String> activationDelivered = java.util.concurrent.ConcurrentHashMap.newKeySet();

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

            var status = repo.findBySn(sn).orElseGet(() -> new DeviceStatus(sn));
            status.setOnline(online);
            status.setLastSeenAt(LocalDateTime.now());
            if (!online) {
                status.setLastLwtAt(LocalDateTime.now());
            }
            repo.save(status);

            log.debug("[MQTT] Device status: sn={}, online={}", sn, online);

            // 029 设备激活：设备在线且尚未成功送达 matching 时触发，推进 PENDING_ACTIVATE → STAGE_1。
            // 用「是否已成功送达」而非「上线边沿」门控：避免首个心跳调用失败（matching 短暂不可用）后
            // 设备保持在线却永不重试而卡死 PENDING_ACTIVATE（codex P1）。送达成功（含已激活）即停止重试；
            // matching 侧 CAS 幂等（非 PENDING_ACTIVATE 即 no-op），重复调用安全。
            if (online && !activationDelivered.contains(sn)) {
                if (activationClient.activate(sn)) {
                    activationDelivered.add(sn);
                }
            } else if (!online) {
                // 离线后允许重新激活尝试（如设备重装/重新入网后再次走撮合履约）。
                activationDelivered.remove(sn);
            }
        } catch (Exception e) {
            log.error("[MQTT] Failed to process status from sn={}: {}", sn, e.getMessage());
        }
    }
}
