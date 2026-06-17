package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchBindSnUseCaseTest {

    @Mock DeviceAssetRepository deviceAssetRepo;
    @Mock AuditLogService auditLog;
    @InjectMocks BatchBindSnUseCase useCase;

    /** 构造一个未绑定 + PENDING_MATCH 的设备 mock。 */
    private DeviceAsset device(long id, DeviceAsset.Stage stage, String sn) {
        DeviceAsset d = org.mockito.Mockito.mock(DeviceAsset.class);
        lenient().when(d.getId()).thenReturn(id);
        lenient().when(d.getStage()).thenReturn(stage);
        lenient().when(d.getSn()).thenReturn(sn);
        return d;
    }

    @Test
    void autoAssignMode_success() {
        List<DeviceAsset> pool = List.of(
                device(1L, DeviceAsset.Stage.PENDING_MATCH, null),
                device(2L, DeviceAsset.Stage.PENDING_MATCH, null),
                device(3L, DeviceAsset.Stage.PENDING_MATCH, null));
        when(deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, 3))
                .thenReturn(pool);
        pool.forEach(d -> when(deviceAssetRepo.findById(d.getId())).thenReturn(java.util.Optional.of(d)));
        when(deviceAssetRepo.existsBySn(anyString())).thenReturn(false);

        var result = useCase.batchBindSn(99L, List.of("SN-A", "SN-B", "SN-C"), null);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.bound()).isEqualTo(3);
        assertThat(result.skipped()).isEmpty();
        verify(deviceAssetRepo, times(3)).save(any());
        verify(auditLog, times(3)).log(eq(99L), eq("BATCH_BIND_SN"), eq("device_asset"), anyString(), anyString());
    }

    @Test
    void specifiedMode_success() {
        DeviceAsset d1 = device(10L, DeviceAsset.Stage.PENDING_MATCH, null);
        DeviceAsset d2 = device(11L, DeviceAsset.Stage.PENDING_MATCH, null);
        DeviceAsset d3 = device(12L, DeviceAsset.Stage.PENDING_MATCH, null);
        when(deviceAssetRepo.findById(10L)).thenReturn(java.util.Optional.of(d1));
        when(deviceAssetRepo.findById(11L)).thenReturn(java.util.Optional.of(d2));
        when(deviceAssetRepo.findById(12L)).thenReturn(java.util.Optional.of(d3));
        when(deviceAssetRepo.existsBySn(anyString())).thenReturn(false);

        var result = useCase.batchBindSn(99L,
                List.of("SN-A", "SN-B", "SN-C"),
                List.of(10L, 11L, 12L));

        assertThat(result.bound()).isEqualTo(3);
        assertThat(result.skipped()).isEmpty();
        verify(deviceAssetRepo, never()).findUnboundByStageOrderByIdAsc(any(), anyInt());
        verify(deviceAssetRepo, times(3)).save(any());
    }

    @Test
    void duplicateSn_skipped() {
        List<DeviceAsset> pool = List.of(
                device(1L, DeviceAsset.Stage.PENDING_MATCH, null),
                device(2L, DeviceAsset.Stage.PENDING_MATCH, null));
        when(deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, 2))
                .thenReturn(pool);
        pool.forEach(d -> when(deviceAssetRepo.findById(d.getId())).thenReturn(java.util.Optional.of(d)));
        when(deviceAssetRepo.existsBySn("SN-A")).thenReturn(true);
        when(deviceAssetRepo.existsBySn("SN-B")).thenReturn(false);

        var result = useCase.batchBindSn(99L, List.of("SN-A", "SN-B"), null);

        assertThat(result.bound()).isEqualTo(1);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).sn()).isEqualTo("SN-A");
        assertThat(result.skipped().get(0).reason()).isEqualTo("SN 已被使用");
    }

    @Test
    void deviceStageWrong_skipped() {
        DeviceAsset locked = device(1L, DeviceAsset.Stage.LOCKED, null);
        when(deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, 1))
                .thenReturn(List.of(locked));
        when(deviceAssetRepo.findById(1L)).thenReturn(java.util.Optional.of(locked));

        var result = useCase.batchBindSn(99L, List.of("SN-A"), null);

        assertThat(result.bound()).isEqualTo(0);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).reason()).isEqualTo("设备状态不允许绑定 SN");
        verify(deviceAssetRepo, never()).save(any());
    }

    @Test
    void snCountExceedsMax_throws() {
        List<String> tooMany = IntStream.rangeClosed(1, 201)
                .mapToObj(i -> "SN-" + i)
                .collect(Collectors.toList());

        assertThatThrownBy(() -> useCase.batchBindSn(99L, tooMany, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservedPrefixSn_skipped() {
        DeviceAsset d = device(1L, DeviceAsset.Stage.PENDING_MATCH, null);
        when(deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, 1))
                .thenReturn(List.of(d));

        var result = useCase.batchBindSn(99L, List.of("SN-PENDING-xxx"), null);

        assertThat(result.bound()).isEqualTo(0);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).reason()).isEqualTo("SN 保留字前缀不允许");
        verify(deviceAssetRepo, never()).save(any());
    }

    @Test
    void moreSnsThanDevices_extraSkipped() {
        DeviceAsset only = device(1L, DeviceAsset.Stage.PENDING_MATCH, null);
        when(deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, 3))
                .thenReturn(List.of(only));
        when(deviceAssetRepo.findById(1L)).thenReturn(java.util.Optional.of(only));
        when(deviceAssetRepo.existsBySn("SN-A")).thenReturn(false);

        var result = useCase.batchBindSn(99L, List.of("SN-A", "SN-B", "SN-C"), null);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.bound()).isEqualTo(1);
        assertThat(result.skipped()).hasSize(2);
        assertThat(result.skipped()).allMatch(s -> s.reason().equals("无匹配设备"));
        assertThat(result.skipped().stream().map(BatchBindSnUseCase.SkipEntry::sn))
                .containsExactlyInAnyOrder("SN-B", "SN-C");
    }
}
