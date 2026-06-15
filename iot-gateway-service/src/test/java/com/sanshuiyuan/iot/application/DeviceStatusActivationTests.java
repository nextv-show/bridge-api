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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 029：DeviceStatusConsumer 仅在「上线边沿」（offline/未知 → online）触发激活，避免每条心跳都打 matching。
 */
@ExtendWith(MockitoExtension.class)
class DeviceStatusActivationTests {

    @Mock DeviceStatusRepository repo;
    @Mock MatchingActivationClient activationClient;

    private DeviceStatusConsumer consumer() {
        return new DeviceStatusConsumer(repo, new ObjectMapper(), activationClient);
    }

    private static byte[] status(boolean online) {
        return ("{\"online\":" + online + "}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void firstSeenOnline_triggersActivate() {
        when(repo.findBySn("SN-A")).thenReturn(Optional.empty());   // 未知 → online
        consumer().onStatus("SN-A", status(true));
        verify(activationClient).activate(eq("SN-A"));
    }

    @Test
    void offlineToOnline_edge_triggersActivate() {
        DeviceStatus prev = new DeviceStatus("SN-B");
        prev.setOnline(false);
        when(repo.findBySn("SN-B")).thenReturn(Optional.of(prev));
        consumer().onStatus("SN-B", status(true));
        verify(activationClient).activate(eq("SN-B"));
    }

    @Test
    void alreadyOnline_noEdge_doesNotTrigger() {
        DeviceStatus prev = new DeviceStatus("SN-C");
        prev.setOnline(true);
        when(repo.findBySn("SN-C")).thenReturn(Optional.of(prev));
        consumer().onStatus("SN-C", status(true));
        verify(activationClient, never()).activate(eq("SN-C"));
    }

    @Test
    void goingOffline_doesNotTrigger() {
        DeviceStatus prev = new DeviceStatus("SN-D");
        prev.setOnline(true);
        when(repo.findBySn("SN-D")).thenReturn(Optional.of(prev));
        consumer().onStatus("SN-D", status(false));
        verify(activationClient, never()).activate(eq("SN-D"));
    }
}
