package com.sanshuiyuan.h5.myorders.application;

import com.sanshuiyuan.h5.checkout.domain.DeviceSpec;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.myorders.dto.OrderSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MyOrdersQueryService {

    private final H5OrderRepository orderRepository;
    private final DeviceSpecRepository specRepository;

    public MyOrdersQueryService(H5OrderRepository orderRepository,
                                DeviceSpecRepository specRepository) {
        this.orderRepository = orderRepository;
        this.specRepository = specRepository;
    }

    public Page<OrderSummaryDTO> list(String openid, int page, int size) {
        int safeSize = Math.min(size, 50);
        PageRequest pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<H5Order> orders = orderRepository.findByOpenidOrderByCreatedAtDesc(openid, pageable);

        // 批量查设备名称，避免 N+1
        Map<String, String> modelNameMap = specRepository.findAll().stream()
                .collect(Collectors.toMap(DeviceSpec::getModelCode, DeviceSpec::getName,
                        (a, b) -> a)); // 同 modelCode 保留第一条

        return orders.map(o -> OrderSummaryDTO.of(
                o.getId(),
                o.getOrderNo(),
                o.getSn(),
                o.getModelCode(),
                modelNameMap.getOrDefault(o.getModelCode(), o.getModelCode()),
                o.getAmountCents(),
                o.getStatus().name(),
                o.getCooldownEndAt(),
                o.getPaymentChannel(),
                o.getCreatedAt()
        ));
    }
}
