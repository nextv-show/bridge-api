package com.sanshuiyuan.h5.rebate.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 返利冻结配置（{@code h5.rebate.*}）。
 *
 * <p>分账算法待运营确认，金额暂以配置占位值落库（默认 0）；冷静期时长与订单退款冷静期保持一致（默认 24h）。
 */
@Component
@ConfigurationProperties(prefix = "h5.rebate")
public class RebateProperties {

    /** L1（直接邀请人）返利占位金额（分）。 */
    private long l1AmountCents = 0L;

    /** L2（间接邀请人）返利占位金额（分）。 */
    private long l2AmountCents = 0L;

    /** 冷静期时长（小时）：FROZEN 满此时长后解冻为 CONFIRMED。默认 24，与订单退款冷静期一致。 */
    private long cooldownHours = 24L;

    public long getL1AmountCents() { return l1AmountCents; }
    public void setL1AmountCents(long l1AmountCents) { this.l1AmountCents = l1AmountCents; }

    public long getL2AmountCents() { return l2AmountCents; }
    public void setL2AmountCents(long l2AmountCents) { this.l2AmountCents = l2AmountCents; }

    public long getCooldownHours() { return cooldownHours; }
    public void setCooldownHours(long cooldownHours) { this.cooldownHours = cooldownHours; }
}
