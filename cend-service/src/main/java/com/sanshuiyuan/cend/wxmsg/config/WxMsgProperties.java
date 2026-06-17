package com.sanshuiyuan.cend.wxmsg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wx.tpl")
public class WxMsgProperties {

    private String paySuccess = "";
    private String refundSuccess = "";
    private String claimConfirmRemind = "";
    private String miniClaimConfirmRemind = "";

    public String getPaySuccess() { return paySuccess; }
    public void setPaySuccess(String paySuccess) { this.paySuccess = paySuccess; }

    public String getRefundSuccess() { return refundSuccess; }
    public void setRefundSuccess(String refundSuccess) { this.refundSuccess = refundSuccess; }

    public String getClaimConfirmRemind() { return claimConfirmRemind; }
    public void setClaimConfirmRemind(String claimConfirmRemind) { this.claimConfirmRemind = claimConfirmRemind; }

    public String getMiniClaimConfirmRemind() { return miniClaimConfirmRemind; }
    public void setMiniClaimConfirmRemind(String miniClaimConfirmRemind) { this.miniClaimConfirmRemind = miniClaimConfirmRemind; }

    public boolean isPaySuccessConfigured() {
        return paySuccess != null && !paySuccess.isBlank();
    }

    public boolean isRefundSuccessConfigured() {
        return refundSuccess != null && !refundSuccess.isBlank();
    }

    public boolean isClaimConfirmRemindConfigured() {
        return claimConfirmRemind != null && !claimConfirmRemind.isBlank();
    }

    public boolean isMiniClaimConfirmRemindConfigured() {
        return miniClaimConfirmRemind != null && !miniClaimConfirmRemind.isBlank();
    }
}
