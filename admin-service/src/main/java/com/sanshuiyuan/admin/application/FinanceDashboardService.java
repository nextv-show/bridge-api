package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import com.sanshuiyuan.admin.infra.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FinanceDashboardService {

    private final OrderRepository orderRepo;
    private final DeviceAssetRepository deviceAssetRepo;

    public FinanceDashboardService(OrderRepository orderRepo,
                                   DeviceAssetRepository deviceAssetRepo) {
        this.orderRepo = orderRepo;
        this.deviceAssetRepo = deviceAssetRepo;
    }

    public Map<String, Object> getOverview() {
        long totalPaidOrders = orderRepo.countPaid();
        long totalRevenueCents = orderRepo.sumPaidAmountCents();
        long totalDevices = deviceAssetRepo.countTotal();
        long boundDevices = deviceAssetRepo.countBound();

        return Map.of(
            "totalPaidOrders", totalPaidOrders,
            "totalRevenueCents", totalRevenueCents,
            "totalDevices", totalDevices,
            "boundDevices", boundDevices,
            "pendingBindDevices", totalDevices - boundDevices
        );
    }
}
