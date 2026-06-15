package com.sanshuiyuan.iot.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.iot.domain.DeviceStatus;
import com.sanshuiyuan.iot.infra.repository.DeviceStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 029：DeviceStatusConsumer 激活触发。门控 = 「在线 且 尚未成功送达 matching」（非上线边沿），
 * 以保证首个心跳调用失败后能在后续心跳重试，不会因设备保持在线而卡死（codex P1）。
 */
@ExtendWith(MockitoExtension.class)
class DeviceStatusActivationTests {

    @Mock DeviceStatusRepository repo;
    @Mock MatchingActivationClient activationClient;

    private DeviceStatusConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(repo.findBySn(any())).thenReturn(Optional.empty());
        consumer = new DeviceStatusConsumer(repo, new ObjectMapper(), activationClient);
    }

    private static byte[] status(boolean online) {
        return ("{\"online\":" + online + "}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void online_triggersActivate_once_afterSuccessfulDelivery() {
        when(activationClient.activate("SN-A")).thenReturn(true);   // matching 成功处理

        consumer.onStatus("SN-A", status(true));
        consumer.onStatus("SN-A", status(true));   // 已送达 → 不再调

        verify(activationClient, times(1)).activate(eq("SN-A"));
    }

    @Test
    void failedDelivery_retriesOnNextOnlineHeartbeat() {
        when(activationClient.activate("SN-B")).thenReturn(false).thenReturn(true);

        consumer.onStatus("SN-B", status(true));   // 失败
        consumer.onStatus("SN-B", status(true));   // 重试 → 成功
        consumer.onStatus("SN-B", status(true));   // 已送达 → 不再调

        verify(activationClient, times(2)).activate(eq("SN-B"));
    }

    @Test
    void offline_doesNotTrigger_andResetsDeliveryForReactivation() {
        when(activationClient.activate("SN-C")).thenReturn(true);

        consumer.onStatus("SN-C", status(true));    // 调一次（成功送达）
        consumer.onStatus("SN-C", status(false));   // 离线：不调 + 清除送达标记
        consumer.onStatus("SN-C", status(true));    // 再次上线 → 重新调（设备可能重装/重新入网）

        verify(activationClient, times(2)).activate(eq("SN-C"));
    }

    @Test
    void offlineStatus_neverTriggers() {
        consumer.onStatus("SN-D", status(false));
        verify(activationClient, never()).activate(any());
    }
}
