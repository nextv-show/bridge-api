package com.sanshuiyuan.cend.myorders.application;

import com.sanshuiyuan.cend.checkout.domain.DeviceSpec;
import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.identity.IdentityResolver;
import com.sanshuiyuan.cend.myorders.dto.OrderSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MyOrdersQueryService {

    private final CendOrderRepository orderRepository;
    private final DeviceSpecRepository specRepository;
    private final IdentityResolver identityResolver;

    public MyOrdersQueryService(CendOrderRepository orderRepository,
                                DeviceSpecRepository specRepository,
                                IdentityResolver identityResolver) {
        this.orderRepository = orderRepository;
        this.specRepository = specRepository;
        this.identityResolver = identityResolver;
    }

    public Page<OrderSummaryDTO> list(String openid, int page, int size) {
        int safeSize = Math.min(size, 50);
        PageRequest pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 按自然人聚合：解析当前 openid 同人名下的全部 openid（含自身），让小程序/公众号同一用户订单合并可见。
        // 未实名时只返回自身，等价于原"按本端 openid 查"行为。
        Set<String> owned = identityResolver.resolveOwnedOpenids(openid);
        Page<CendOrder> orders = orderRepository.findByOpenidInOrderByCreatedAtDesc(owned, pageable);

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
