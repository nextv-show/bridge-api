package com.sanshuiyuan.asset.rebate.api.dto;

import com.sanshuiyuan.asset.rebate.domain.PendingRebate;
import com.sanshuiyuan.asset.rebate.domain.RebateStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 返利列表项（对外视图）。
 *
 * <p><b>金额可见性合规铁律：</b>仅 {@code CONFIRMED}（已确认）暴露 {@code amountCents}；
 * {@code FROZEN}（冷静期中）与 {@code CANCELLED}（已取消）一律返回 {@code null}，
 * 冷静期内不得展示任何具体金额。该规则在此工厂方法集中收敛，禁止在别处旁路。
 *
 * <p>不含任何关系链层级字段（level 仅为返利档位标识，非关系深度入口）。
 *
 * @param orderId 触发订单引用（受益来源购机订单 id）
 * @param level   受益层级 L1/L2
 * @param status  返利状态 FROZEN/CONFIRMED/CANCELLED
 * @param amountCents 金额（分），仅 CONFIRMED 非空
 */
public record PendingRebateItem(
        Long id,
        Long orderId,
        String level,
        String status,
        Long amountCents,
        String frozenAt,
        String confirmedAt
) {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    public static PendingRebateItem from(PendingRebate r) {
        Long amount = r.getStatus() == RebateStatus.CONFIRMED ? r.getAmountCents() : null;
        return new PendingRebateItem(
                r.getId(),
                r.getOrderId(),
                r.getLevel().name(),
                r.getStatus().name(),
                amount,
                fmt(r.getFrozenAt()),
                fmt(r.getConfirmedAt())
        );
    }

    private static String fmt(LocalDateTime dt) {
        return dt == null ? null : dt.atZone(ZoneId.systemDefault()).format(ISO_FMT);
    }
}
