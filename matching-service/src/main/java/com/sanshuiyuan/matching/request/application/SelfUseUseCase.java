package com.sanshuiyuan.matching.request.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.logistics.domain.LogisticsOutboxEntry;
import com.sanshuiyuan.matching.logistics.infra.LogisticsOutboxRepository;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.SelfUseRequest;
import com.sanshuiyuan.matching.request.api.dto.SelfUseResponse;
import com.sanshuiyuan.matching.request.domain.DeviceStage;
import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SelfUseUseCase {

    private final MatchingUserResolver userResolver;
    private final DeviceAssetGateway deviceAssetGateway;
    private final LogisticsOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SelfUseUseCase(MatchingUserResolver userResolver,
                          DeviceAssetGateway deviceAssetGateway,
                          LogisticsOutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.userResolver = userResolver;
        this.deviceAssetGateway = deviceAssetGateway;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SelfUseResponse selfUse(String subject, long deviceAssetId, SelfUseRequest req) {
        long userId = userResolver.resolveUserId(subject);

        // CAS: PENDING_MATCH → SELF_USE（不可逆终态）
        int staged = deviceAssetGateway.advanceStage(
                deviceAssetId, userId, DeviceStage.PENDING_MATCH, DeviceStage.SELF_USE);
        if (staged != 1) {
            throw ApiException.conflict("DEVICE_STAGE_INVALID",
                    "设备状态不允许自用注册（可能已被匹配或已选择路径）");
        }

        // 写物流 outbox（source=SELF_USE, request_id=null）
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ship_to_name", req.getShip_to_name());
            payload.put("ship_to_phone", req.getShip_to_phone());
            payload.put("ship_to_address", req.getShip_to_address());
            payload.put("lat", req.getLat());
            payload.put("lng", req.getLng());
            payload.put("source", "SELF_USE");
            String payloadJson = objectMapper.writeValueAsString(payload);

            LogisticsOutboxEntry outbox = new LogisticsOutboxEntry();
            outbox.setRequestId(null);
            outbox.setDeviceAssetId(deviceAssetId);
            outbox.setSource("SELF_USE");
            outbox.setPayloadJson(payloadJson);
            outboxRepository.saveAndFlush(outbox);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("序列化物流 payload 失败", e);
        }

        return new SelfUseResponse(deviceAssetId, "SELF_USE", true);
    }
}
