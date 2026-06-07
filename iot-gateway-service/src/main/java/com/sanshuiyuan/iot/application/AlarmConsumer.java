package com.sanshuiyuan.iot.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.iot.domain.AlarmType;
import com.sanshuiyuan.iot.domain.AlarmsOutbox;
import com.sanshuiyuan.iot.domain.DeviceAlarm;
import com.sanshuiyuan.iot.domain.Severity;
import com.sanshuiyuan.iot.infra.repository.AlarmsOutboxRepository;
import com.sanshuiyuan.iot.infra.repository.DeviceAlarmRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 告警消费者。落库 device_alarms（按 event_id 幂等），并写 alarms_outbox 供 008 异步消费。
 */
@Service
public class AlarmConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlarmConsumer.class);

    private final DeviceAlarmRepository alarmRepo;
    private final AlarmsOutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public AlarmConsumer(DeviceAlarmRepository alarmRepo, AlarmsOutboxRepository outboxRepo,
                         ObjectMapper objectMapper) {
        this.alarmRepo = alarmRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    public void onAlarm(String sn, byte[] payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String alarmTypeStr = json.get("alarm_type").asText();
            String severityStr = json.has("severity") ? json.get("severity").asText() : "WARN";
            String eventId = json.has("event_id") && !json.get("event_id").isNull()
                    ? json.get("event_id").asText() : null;
            String payloadStr = json.has("payload") ? json.get("payload").toString() : null;

            // 幂等：同一 event_id 不重复落库
            if (eventId != null && alarmRepo.findByExternalEventId(eventId).isPresent()) {
                log.debug("[MQTT] Duplicate alarm event_id={}, skipping", eventId);
                return;
            }

            var alarm = new DeviceAlarm(sn, AlarmType.valueOf(alarmTypeStr),
                    Severity.valueOf(severityStr), eventId, payloadStr, LocalDateTime.now());
            alarm = alarmRepo.save(alarm);

            // 写 alarms_outbox 供 008 消费
            var outbox = new AlarmsOutbox(alarm.getId(), payloadStr != null ? payloadStr : "{}");
            outboxRepo.save(outbox);

            log.info("[MQTT] Alarm raised: sn={}, type={}, severity={}", sn, alarmTypeStr, severityStr);
        } catch (Exception e) {
            log.error("[MQTT] Failed to process alarm from sn={}: {}", sn, e.getMessage());
        }
    }
}
