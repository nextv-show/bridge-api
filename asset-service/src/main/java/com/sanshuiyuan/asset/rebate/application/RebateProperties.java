package com.sanshuiyuan.asset.rebate.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐返利配置（{@code asset.rebate.*}）。
 *
 * <p><b>注意：</b>返利金额不在此配置——金额按机型（SKU）固定费率列
 * {@code referral_fee_l1_cents / referral_fee_l2_cents} 决定并在冻结时快照。此处仅保留冷静期时长。
 */
@Component
@ConfigurationProperties(prefix = "asset.rebate")
public class RebateProperties {

    /** 冷静期时长（小时）：FROZEN 满此时长后解冻为 CONFIRMED。默认 24，与购机退款冷静期一致。 */
    private long cooldownHours = 24L;

    public long getCooldownHours() { return cooldownHours; }
    public void setCooldownHours(long cooldownHours) { this.cooldownHours = cooldownHours; }
}
