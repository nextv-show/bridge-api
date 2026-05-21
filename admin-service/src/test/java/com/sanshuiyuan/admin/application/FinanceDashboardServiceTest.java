package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import com.sanshuiyuan.admin.infra.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceDashboardServiceTest {

    @Mock private OrderRepository orderRepo;
    @Mock private DeviceAssetRepository deviceAssetRepo;
    private FinanceDashboardService service;

    @BeforeEach
    void setUp() {
        service = new FinanceDashboardService(orderRepo, deviceAssetRepo);
    }

    @Test
    void getOverview_aggregatesCorrectly() {
        when(orderRepo.countPaid()).thenReturn(42L);
        when(orderRepo.sumPaidAmountCents()).thenReturn(990000L);
        when(deviceAssetRepo.countTotal()).thenReturn(100L);
        when(deviceAssetRepo.countBound()).thenReturn(75L);

        Map<String, Object> result = service.getOverview();

        assertEquals(42L, result.get("totalPaidOrders"));
        assertEquals(990000L, result.get("totalRevenueCents"));
        assertEquals(100L, result.get("totalDevices"));
        assertEquals(75L, result.get("boundDevices"));
        assertEquals(25L, result.get("pendingBindDevices"));
    }

    @Test
    void getOverview_zeroData() {
        when(orderRepo.countPaid()).thenReturn(0L);
        when(orderRepo.sumPaidAmountCents()).thenReturn(0L);
        when(deviceAssetRepo.countTotal()).thenReturn(0L);
        when(deviceAssetRepo.countBound()).thenReturn(0L);

        Map<String, Object> result = service.getOverview();

        assertEquals(0L, result.get("totalPaidOrders"));
        assertEquals(0L, result.get("totalRevenueCents"));
        assertEquals(0L, result.get("totalDevices"));
        assertEquals(0L, result.get("boundDevices"));
        assertEquals(0L, result.get("pendingBindDevices"));
    }

    @Test
    void getOverview_allDevicesBound() {
        when(orderRepo.countPaid()).thenReturn(10L);
        when(orderRepo.sumPaidAmountCents()).thenReturn(50000L);
        when(deviceAssetRepo.countTotal()).thenReturn(50L);
        when(deviceAssetRepo.countBound()).thenReturn(50L);

        Map<String, Object> result = service.getOverview();

        assertEquals(0L, result.get("pendingBindDevices"));
    }
}
