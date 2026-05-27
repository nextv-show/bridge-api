package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.api.dto.OrderCancelRequest;
import com.sanshuiyuan.admin.api.dto.OrderCreateRequest;
import com.sanshuiyuan.admin.api.dto.OrderShipRequest;
import com.sanshuiyuan.admin.api.dto.OrderTimelineDto;
import com.sanshuiyuan.admin.application.OrderAdminService;
import com.sanshuiyuan.admin.domain.DeviceAsset;
import com.sanshuiyuan.admin.domain.Order;
import com.sanshuiyuan.admin.domain.Sku;
import com.sanshuiyuan.admin.domain.User;
import com.sanshuiyuan.admin.infra.repository.DeviceAssetRepository;
import com.sanshuiyuan.admin.infra.repository.OrderRepository;
import com.sanshuiyuan.admin.infra.repository.SkuRepository;
import com.sanshuiyuan.admin.infra.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/admin/orders")
public class OrderAdminController {

    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final DeviceAssetRepository deviceAssetRepo;
    private final SkuRepository skuRepo;
    private final OrderAdminService orderService;

    public OrderAdminController(OrderRepository orderRepo,
                                UserRepository userRepo,
                                DeviceAssetRepository deviceAssetRepo,
                                SkuRepository skuRepo,
                                OrderAdminService orderService) {
        this.orderRepo = orderRepo;
        this.userRepo = userRepo;
        this.deviceAssetRepo = deviceAssetRepo;
        this.skuRepo = skuRepo;
        this.orderService = orderService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String channel,
                                    @RequestParam(required = false) String paymentMethod,
                                    @RequestParam(required = false) String startDate,
                                    @RequestParam(required = false) String endDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String keyword = q == null || q.isBlank() ? null : "%" + q.trim() + "%";
        Order.Status orderStatus = parseStatus(status);
        LocalDateTime startAt = parseStart(startDate);
        LocalDateTime endAt = parseEnd(endDate);

        Page<Order> records = orderRepo.search(orderStatus, blankToNull(channel), blankToNull(paymentMethod), keyword, startAt, endAt, pageable);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", records.map(this::toSummaryDto).getContent());
        result.put("total", records.getTotalElements());
        result.put("page", records.getNumber());
        result.put("size", records.getSize());
        return result;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        long total = 0L;
        for (Order.Status status : Order.Status.values()) {
            long count = orderRepo.countByStatus(status);
            statusCounts.put(status.name(), count);
            total += count;
        }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("todayOrders", orderRepo.countPaidSince(todayStart));
        kpis.put("todayGmvCents", orderRepo.sumPaidAmountCentsSince(todayStart));
        kpis.put("pendingOrders", statusCounts.getOrDefault(Order.Status.PENDING_PAY.name(), 0L));
        kpis.put("shippingOrders", statusCounts.getOrDefault(Order.Status.SHIPPED.name(), 0L) + statusCounts.getOrDefault(Order.Status.ACTIVATED.name(), 0L));
        kpis.put("abnormalOrders", statusCounts.getOrDefault(Order.Status.REFUNDING.name(), 0L) + statusCounts.getOrDefault(Order.Status.CANCELLED.name(), 0L));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("statusCounts", statusCounts);
        result.put("kpis", kpis);
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Order order = getOrder(id);
        return toDetailDto(order);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody OrderCreateRequest req, Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        Order order = new Order();
        order.setUserId(req.userId());
        order.setSkuId(req.skuId());
        order.setQty(req.qty() != null ? req.qty() : 1);
        order.setAmountCents(req.amountCents() != null ? req.amountCents() : 0L);
        order.setStatus(Order.Status.PAID);
        order.setChannel(blankToNull(req.channel()));
        order.setPaymentMethod(blankToNull(req.paymentMethod()));
        order.setWxTransactionId(req.paymentTxnId());
        order.setDeviceAssetId(req.deviceAssetId());
        order.setAddressSnapshot(req.addressSnapshot() != null ? req.addressSnapshot() : "{}");
        if (req.createdAt() != null && !req.createdAt().isBlank()) {
            order.setCreatedAt(LocalDateTime.parse(req.createdAt()));
        }
        if (req.paidAt() != null && !req.paidAt().isBlank()) {
            order.setPaidAt(LocalDateTime.parse(req.paidAt()));
        }
        Order saved = orderService.create(adminId, resolveOperator(auth), order, orderPayload(req));
        return toDetailDto(saved);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id,
                                      @RequestBody(required = false) OrderCancelRequest req,
                                      Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        Order saved = orderService.cancel(adminId, resolveOperator(auth), getOrder(id), req != null ? req.reason() : null);
        return toDetailDto(saved);
    }

    @PostMapping("/{id}/ship")
    public Map<String, Object> ship(@PathVariable Long id,
                                    @RequestBody(required = false) OrderShipRequest req,
                                    Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        Order saved = orderService.ship(adminId, resolveOperator(auth), getOrder(id), req != null ? req.shippingNo() : null);
        return toDetailDto(saved);
    }

    @PostMapping("/{id}/deliver")
    public Map<String, Object> deliver(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, String> body,
                                       Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        Order saved = orderService.markDelivered(adminId, resolveOperator(auth), getOrder(id), body != null ? body.get("note") : null);
        return toDetailDto(saved);
    }

    private Order getOrder(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private Order.Status parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        return Order.Status.valueOf(status);
    }

    private LocalDateTime parseStart(String startDate) {
        if (startDate == null || startDate.isBlank()) return null;
        return LocalDate.parse(startDate).atStartOfDay();
    }

    private LocalDateTime parseEnd(String endDate) {
        if (endDate == null || endDate.isBlank()) return null;
        return LocalDate.parse(endDate).plusDays(1).atStartOfDay();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private String resolveOperator(Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof String s && !s.isBlank()) {
            return s;
        }
        return auth.getName();
    }

    private Map<String, Object> toSummaryDto(Order order) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", order.getId());
        dto.put("userId", order.getUserId());
        dto.put("skuId", order.getSkuId());
        dto.put("skuName", resolveSkuName(order.getSkuId()));
        dto.put("qty", order.getQty());
        dto.put("amountCents", order.getAmountCents());
        dto.put("status", order.getStatus().name());
        dto.put("channel", order.getChannel());
        dto.put("paymentMethod", order.getPaymentMethod());
        dto.put("paymentDisplay", paymentDisplay(order.getPaymentMethod()));
        dto.put("userName", resolveUserName(order.getUserId()));
        dto.put("userPhoneMask", resolvePhoneMask(order.getUserId()));
        dto.put("userCity", resolveCity(order.getUserId()));
        dto.put("deviceSn", resolveDeviceSn(order.getId()));
        dto.put("deviceModel", resolveDeviceModel(order.getId()));
        dto.put("deviceStage", resolveDeviceStage(order.getId()));
        dto.put("installAddr", addressField(order.getAddressSnapshot()));
        dto.put("shippingNo", order.getShippingNo());
        dto.put("cancelReason", order.getCancelReason());
        dto.put("createdAt", fmt(order.getCreatedAt()));
        dto.put("paidAt", fmt(order.getPaidAt()));
        dto.put("paymentDeadlineAt", fmt(order.getPaymentDeadlineAt()));
        dto.put("lastRemindedAt", fmt(order.getLastRemindedAt()));
        dto.put("shippedAt", fmt(order.getShippedAt()));
        dto.put("deliveredAt", fmt(order.getDeliveredAt()));
        dto.put("cancelledAt", fmt(order.getCancelledAt()));
        dto.put("updatedAt", fmt(order.getUpdatedAt()));
        dto.put("paymentTxnId", order.getWxTransactionId());
        return dto;
    }

    private Map<String, Object> toDetailDto(Order order) {
        Map<String, Object> dto = toSummaryDto(order);
        Map<String, Object> addressSnapshot = new LinkedHashMap<>();
        addressSnapshot.put("raw", order.getAddressSnapshot());
        dto.put("addressSnapshot", addressSnapshot);
        dto.put("timeline", buildTimeline(order));
        dto.put("userInfo", userInfo(order.getUserId()));
        dto.put("device", deviceInfo(order.getId()));
        dto.put("breakdown", breakdown(order));
        return dto;
    }

    private Map<String, Object> userInfo(Long userId) {
        return userRepo.findById(userId)
                .map(u -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", u.getId());
                    dto.put("name", u.getNickname());
                    dto.put("phoneMask", u.getPhoneMask());
                    dto.put("realNameMask", u.getRealNameMask());
                    dto.put("city", u.getCity());
                    dto.put("channel", u.getChannel());
                    dto.put("tier", u.getTier());
                    dto.put("kycStatus", u.getKycStatus());
                    return dto;
                })
                .orElseGet(() -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", userId);
                    return dto;
                });
    }

    private Map<String, Object> deviceInfo(Long orderId) {
        return resolveDevice(orderId)
                .map(d -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", d.getId());
                    dto.put("sn", d.getSn());
                    dto.put("model", d.getModel());
                    dto.put("stage", d.getStage() != null ? d.getStage().name() : "");
                    dto.put("purchasedAt", fmt(d.getPurchasedAt()));
                    dto.put("incomeCents", d.getCumulativeIncomeCents());
                    dto.put("roiBp", d.getRoiBp());
                    return dto;
                })
                .orElseGet(LinkedHashMap::new);
    }

    private List<Map<String, Object>> breakdown(Order order) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(breakdownItem("商品金额", order.getAmountCents()));
        list.add(breakdownItem("支付方式", paymentDisplay(order.getPaymentMethod())));
        list.add(breakdownItem("渠道", channelDisplay(order.getChannel())));
        list.add(breakdownItem("订单状态", order.getStatus().name()));
        return list;
    }

    private Map<String, Object> breakdownItem(String label, Object value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("value", value);
        return item;
    }

    private List<OrderTimelineDto> buildTimeline(Order order) {
        List<OrderTimelineDto> timeline = new ArrayList<>();
        timeline.add(new OrderTimelineDto("CREATED", "订单创建", fmt(order.getCreatedAt()), "已生成下单记录"));
        if (order.getPaidAt() != null) timeline.add(new OrderTimelineDto("PAID", "支付完成", fmt(order.getPaidAt()), "支付渠道已回调确认"));
        if (order.getShippedAt() != null) timeline.add(new OrderTimelineDto("SHIPPED", "已发货", fmt(order.getShippedAt()), "已同步物流或资产绑定"));
        if (order.getDeliveredAt() != null) timeline.add(new OrderTimelineDto("COMPLETED", "已完成", fmt(order.getDeliveredAt()), "订单生命周期结束"));
        if (order.getCancelledAt() != null) timeline.add(new OrderTimelineDto("CANCELLED", "已取消", fmt(order.getCancelledAt()), order.getCancelReason()));
        return timeline;
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }

    private String paymentDisplay(String method) {
        if (method == null) return "";
        return switch (method) {
            case "WECHAT" -> "微信支付";
            case "ALIPAY" -> "支付宝";
            case "APPLE" -> "Apple Pay";
            case "CARD" -> "银行卡";
            default -> method;
        };
    }

    private String channelDisplay(String channel) {
        if (channel == null) return "";
        return switch (channel) {
            case "WECHAT_MP" -> "微信小程序";
            case "IOS" -> "iOS App";
            case "ANDROID" -> "Android App";
            case "DOUYIN" -> "抖音小店";
            case "H5" -> "H5 落地页";
            default -> channel;
        };
    }

    private String addressField(String snapshot) {
        return snapshot == null ? "" : snapshot;
    }

    private String resolveSkuName(Long skuId) {
        if (skuId == null) return "";
        return skuRepo.findById(skuId).map(Sku::getName).orElse("SKU #" + skuId);
    }

    private String resolveUserName(Long userId) {
        return userRepo.findById(userId).map(User::getNickname).orElse("");
    }

    private String resolvePhoneMask(Long userId) {
        return userRepo.findById(userId).map(User::getPhoneMask).orElse("");
    }

    private String resolveCity(Long userId) {
        return userRepo.findById(userId).map(User::getCity).orElse("");
    }

    private Optional<DeviceAsset> resolveDevice(Long orderId) {
        return deviceAssetRepo.findFirstByOrderId(orderId);
    }

    private String resolveDeviceSn(Long orderId) {
        return resolveDevice(orderId).map(DeviceAsset::getSn).orElse("");
    }

    private String resolveDeviceModel(Long orderId) {
        return resolveDevice(orderId).map(DeviceAsset::getModel).orElse("");
    }

    private String resolveDeviceStage(Long orderId) {
        return resolveDevice(orderId).map(d -> d.getStage() != null ? d.getStage().name() : "").orElse("");
    }

    private String orderPayload(OrderCreateRequest req) {
        return "{\"userId\":" + req.userId() + ",\"skuId\":" + req.skuId() + ",\"qty\":" + (req.qty() != null ? req.qty() : 1) + "}";
    }

    private List<String> resolveRoles(Authentication auth) {
        List<String> roles = new ArrayList<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            roles.add(value.startsWith("ROLE_") ? value.substring(5) : value);
        }
        return roles;
    }
}
