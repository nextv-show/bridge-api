package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock PlatformTransactionManager txManager;
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
        when(deviceAssetRepo.existsBySn(anyString())).thenReturn(false);
        when(deviceAssetRepo.casBindSn(anyLong(), anyString())).thenReturn(1);

        var result = useCase.batchBindSn(99L, List.of("SN-A", "SN-B", "SN-C"), null);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.bound()).isEqualTo(3);
        assertThat(result.skipped()).isEmpty();
        verify(deviceAssetRepo, times(3)).casBindSn(anyLong(), anyString());
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
        when(deviceAssetRepo.casBindSn(anyLong(), anyString())).thenReturn(1);

        var result = useCase.batchBindSn(99L,
                List.of("SN-A", "SN-B", "SN-C"),
                List.of(10L, 11L, 12L));

        assertThat(result.bound()).isEqualTo(3);
        assertThat(result.skipped()).isEmpty();
        verify(deviceAssetRepo, never()).findUnboundByStageOrderByIdAsc(any(), anyInt());
        verify(deviceAssetRepo, times(3)).casBindSn(anyLong(), anyString());
    }

    /** Fix 1：指定模式下缺失设备 ID 不得压缩列表导致 SN↔device 错位。 */
    @Test
    void specifiedMode_missingDeviceId_noMisalignment() {
        DeviceAsset d11 = device(11L, DeviceAsset.Stage.PENDING_MATCH, null);
        when(deviceAssetRepo.findById(99L)).thenReturn(java.util.Optional.empty());
        when(deviceAssetRepo.findById(11L)).thenReturn(java.util.Optional.of(d11));
        when(deviceAssetRepo.existsBySn("SN-B")).thenReturn(false);
        when(deviceAssetRepo.casBindSn(11L, "SN-B")).thenReturn(1);

        // sns=[A,B], ids=[missing,11] —— B 必须绑到 11，A 跳过，不得 A 绑到 11
        var result = useCase.batchBindSn(99L,
                List.of("SN-A", "SN-B"),
                Arrays.asList(99L, 11L));

        assertThat(result.bound()).isEqualTo(1);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).sn()).isEqualTo("SN-A");
        assertThat(result.skipped().get(0).deviceAssetId()).isEqualTo(99L);
        assertThat(result.skipped().get(0).reason()).isEqualTo("设备不存在或无匹配设备");
        // SN-A 绝不能落到 11
        verify(deviceAssetRepo, never()).casBindSn(11L, "SN-A");
        verify(deviceAssetRepo, times(1)).casBindSn(11L, "SN-B");
    }

    @Test
    void duplicateSnInRepo_skipped() {
        List<DeviceAsset> pool = List.of(
                device(1L, DeviceAsset.Stage.PENDING_MATCH, null),
                device(2L, DeviceAsset.Stage.PENDING_MATCH, null));
        when(deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, 2))
                .thenReturn(pool);
        when(deviceAssetRepo.existsBySn("SN-A")).thenReturn(true);
        when(deviceAssetRepo.existsBySn("SN-B")).thenReturn(false);
        when(deviceAssetRepo.casBindSn(2L, "SN-B")).thenReturn(1);

        var result = useCase.batchBindSn(99L, List.of("SN-A", "SN-B"), null);

        assertThat(result.bound()).isEqualTo(1);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).sn()).isEqualTo("SN-A");
        assertThat(result.skipped().get(0).reason()).isEqualTo("SN 已被使用");
    }

    /** Fix 3：CAS affected=0（并发绑定或状态变更）→ 跳过。 */
    @Test
    void casConflict_skipped() {
        DeviceAsset d = device(1L, DeviceAsset.Stage.PENDING_MATCH, null);
        when(deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, 1))
                .thenReturn(List.of(d));
        when(deviceAssetRepo.existsBySn("SN-A")).thenReturn(false);
        when(deviceAssetRepo.casBindSn(1L, "SN-A")).thenReturn(0);

        var result = useCase.batchBindSn(99L, List.of("SN-A"), null);

        assertThat(result.bound()).isEqualTo(0);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).reason()).isEqualTo("设备已被绑定或状态不允许");
        verify(auditLog, never()).log(anyLong(), anyString(), anyString(), anyString(), anyString());
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
        verify(deviceAssetRepo, never()).casBindSn(anyLong(), anyString());
    }

    @Test
    void moreSnsThanDevices_extraSkipped() {
        DeviceAsset only = device(1L, DeviceAsset.Stage.PENDING_MATCH, null);
        when(deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, 3))
                .thenReturn(List.of(only));
        when(deviceAssetRepo.existsBySn("SN-A")).thenReturn(false);
        when(deviceAssetRepo.casBindSn(1L, "SN-A")).thenReturn(1);

        var result = useCase.batchBindSn(99L, List.of("SN-A", "SN-B", "SN-C"), null);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.bound()).isEqualTo(1);
        assertThat(result.skipped()).hasSize(2);
        assertThat(result.skipped()).allMatch(s -> s.reason().equals("设备不存在或无匹配设备"));
        assertThat(result.skipped().stream().map(BatchBindSnUseCase.SkipEntry::sn))
                .containsExactlyInAnyOrder("SN-B", "SN-C");
    }

    /** Fix 5：空白/批次内重复 SN 记入 skipped，total 用原始提交数。 */
    @Test
    void blankAndDuplicateSn_recordedInSkipped() {
        List<DeviceAsset> pool = List.of(
                device(1L, DeviceAsset.Stage.PENDING_MATCH, null),
                device(2L, DeviceAsset.Stage.PENDING_MATCH, null));
        when(deviceAssetRepo.findUnboundByStageOrderByIdAsc(DeviceAsset.Stage.PENDING_MATCH, 2))
                .thenReturn(pool);
        when(deviceAssetRepo.existsBySn(anyString())).thenReturn(false);
        when(deviceAssetRepo.casBindSn(anyLong(), anyString())).thenReturn(1);

        // 提交 4 条：SN-A、空白、SN-A（重复）、SN-B → 清洗后 [SN-A, SN-B]
        var result = useCase.batchBindSn(99L,
                Arrays.asList("SN-A", "   ", "SN-A", "SN-B"), null);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.bound()).isEqualTo(2);
        assertThat(result.skipped()).hasSize(2);
        assertThat(result.skipped().stream().map(BatchBindSnUseCase.SkipEntry::reason))
                .containsExactlyInAnyOrder("SN 为空", "SN 在批次内重复");
    }
}
