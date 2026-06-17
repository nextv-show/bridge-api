package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.asset.DeviceAssetStageEvent;
import com.sanshuiyuan.matching.asset.DeviceAssetStageEventRepository;
import com.sanshuiyuan.matching.request.api.dto.ActivateResponse;
import com.sanshuiyuan.matching.request.domain.DeviceStage;
import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 029 设备激活纯单测（Mockito，不依赖 DB）：CAS 命中→STAGE_1+写事件；未命中→幂等 no-op。 */
@ExtendWith(MockitoExtension.class)
class ActivateDeviceUseCaseTest {

    @Mock DeviceAssetGateway gateway;
    @Mock DeviceAssetStageEventRepository stageEventRepository;
    @Mock MatchingMetrics metrics;
    @Mock OrderSnBindbackNotifier snBindbackNotifier;

    private ActivateDeviceUseCase useCase() {
        return new ActivateDeviceUseCase(gateway, stageEventRepository, metrics, snBindbackNotifier);
    }

    @Test
    void casHit_advancesToStage1_writesEvent_andCounts() {
        when(gateway.activateBySn("SN-1", DeviceStage.PENDING_ACTIVATE, DeviceStage.STAGE_1)).thenReturn(1);
        when(gateway.findIdBySn("SN-1")).thenReturn(2001L);

        ActivateResponse resp = useCase().activate("SN-1");

        assertThat(resp.sn()).isEqualTo("SN-1");
        assertThat(resp.activated()).isTrue();

        ArgumentCaptor<DeviceAssetStageEvent> cap = ArgumentCaptor.forClass(DeviceAssetStageEvent.class);
        verify(stageEventRepository).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getDeviceAssetId()).isEqualTo(2001L);
        assertThat(cap.getValue().getEventType()).isEqualTo("STAGE_1_ACTIVATED");
        verify(metrics).activated();
        verify(metrics, never()).activateNoop();
        // 激活成功后兜底回写真实 SN（device_asset_id 已知）
        verify(snBindbackNotifier).notifyBindSn(2001L, "SN-1");
    }

    @Test
    void casMiss_isIdempotentNoop_noEvent_noError() {
        // 已 STAGE_1+ / 未履约 / 无此 SN → CAS 命中 0 行。
        when(gateway.activateBySn("SN-2", DeviceStage.PENDING_ACTIVATE, DeviceStage.STAGE_1)).thenReturn(0);

        ActivateResponse resp = useCase().activate("SN-2");

        assertThat(resp.activated()).isFalse();
        verify(stageEventRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(metrics).activateNoop();
        verify(metrics, never()).activated();
    }

    @Test
    void blankSn_isNoop_withoutTouchingGateway() {
        ActivateResponse resp = useCase().activate("  ");

        assertThat(resp.activated()).isFalse();
        verifyNoInteractions(gateway, stageEventRepository);
        verify(metrics).activateNoop();
    }
}
