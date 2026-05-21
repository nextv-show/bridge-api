package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BindSnUseCaseTest {

    @Mock private DeviceAssetRepository deviceAssetRepo;
    @Mock private AuditLogService auditLog;
    private BindSnUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new BindSnUseCase(deviceAssetRepo, auditLog);
    }

    private DeviceAsset createAsset(Long id, String sn, DeviceAsset.Stage stage) {
        DeviceAsset asset = new DeviceAsset();
        setField(asset, "id", id);
        asset.setSn(sn);
        setField(asset, "userId", 10L);
        setField(asset, "orderId", 20L);
        setField(asset, "model", "TestModel");
        setField(asset, "stage", stage);
        setField(asset, "purchasedAt", LocalDateTime.now());
        return asset;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void bindSn_success() {
        DeviceAsset asset = createAsset(1L, null, DeviceAsset.Stage.PENDING_MATCH);
        when(deviceAssetRepo.findById(1L)).thenReturn(Optional.of(asset));
        when(deviceAssetRepo.existsBySn("SN-001")).thenReturn(false);

        useCase.bindSn(100L, 1L, "SN-001");

        assertEquals("SN-001", asset.getSn());
        verify(deviceAssetRepo).save(asset);
        verify(auditLog).log(eq(100L), eq("BIND_SN"), eq("device_asset"), eq("1"), contains("SN-001"));
    }

    @Test
    void bindSn_alreadyBound_throwsIllegalState() {
        DeviceAsset asset = createAsset(1L, "SN-OLD", DeviceAsset.Stage.PENDING_MATCH);
        when(deviceAssetRepo.findById(1L)).thenReturn(Optional.of(asset));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> useCase.bindSn(100L, 1L, "SN-NEW"));
        assertTrue(ex.getMessage().contains("设备已绑定 SN"));

        verify(deviceAssetRepo, never()).save(any());
    }

    @Test
    void bindSn_deviceNotFound_throwsIllegalArgument() {
        when(deviceAssetRepo.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> useCase.bindSn(100L, 999L, "SN-001"));
        assertTrue(ex.getMessage().contains("设备资产不存在"));
    }

    @Test
    void bindSn_snAlreadyUsed_throwsIllegalState() {
        DeviceAsset asset = createAsset(1L, null, DeviceAsset.Stage.PENDING_MATCH);
        when(deviceAssetRepo.findById(1L)).thenReturn(Optional.of(asset));
        when(deviceAssetRepo.existsBySn("SN-DUPLICATE")).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> useCase.bindSn(100L, 1L, "SN-DUPLICATE"));
        assertTrue(ex.getMessage().contains("SN 已被使用"));

        verify(deviceAssetRepo, never()).save(any());
    }

    @Test
    void bindSn_wrongStage_throwsIllegalState() {
        DeviceAsset asset = createAsset(1L, null, DeviceAsset.Stage.STAGE_1);
        when(deviceAssetRepo.findById(1L)).thenReturn(Optional.of(asset));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> useCase.bindSn(100L, 1L, "SN-001"));
        assertTrue(ex.getMessage().contains("设备状态不允许绑定 SN"));

        verify(deviceAssetRepo, never()).save(any());
    }

    @Test
    void bindSn_emptySn_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> useCase.bindSn(100L, 1L, ""));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.bindSn(100L, 1L, "   "));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.bindSn(100L, 1L, null));
    }

    @Test
    void bindSn_snTrimmed() {
        DeviceAsset asset = createAsset(1L, null, DeviceAsset.Stage.PENDING_MATCH);
        when(deviceAssetRepo.findById(1L)).thenReturn(Optional.of(asset));
        when(deviceAssetRepo.existsBySn("SN-001")).thenReturn(false);

        useCase.bindSn(100L, 1L, "  SN-001  ");

        assertEquals("SN-001", asset.getSn());
        verify(deviceAssetRepo).save(asset);
    }
}
