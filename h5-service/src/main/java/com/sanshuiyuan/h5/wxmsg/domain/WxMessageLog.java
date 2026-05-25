package com.sanshuiyuan.h5.wxmsg.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "wx_message_logs")
public class WxMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    @Enumerated(EnumType.STRING)
    @Column(name = "msg_type", nullable = false, length = 32)
    private WxMsgType msgType;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "template_id", nullable = false, length = 64)
    private String templateId;

    @Column(name = "wx_msg_id", length = 64)
    private String wxMsgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WxMsgStatus status;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WxMessageLog() {}

    public static WxMessageLog sent(String openid, WxMsgType msgType, Long orderId,
                                    String templateId, String wxMsgId) {
        WxMessageLog log = new WxMessageLog();
        log.openid = openid;
        log.msgType = msgType;
        log.orderId = orderId;
        log.templateId = templateId;
        log.wxMsgId = wxMsgId;
        log.status = WxMsgStatus.SENT;
        log.sentAt = LocalDateTime.now();
        return log;
    }

    public static WxMessageLog failed(String openid, WxMsgType msgType, Long orderId,
                                      String templateId, String errorMsg) {
        WxMessageLog log = new WxMessageLog();
        log.openid = openid;
        log.msgType = msgType;
        log.orderId = orderId;
        log.templateId = templateId;
        log.status = WxMsgStatus.FAILED;
        log.errorMsg = errorMsg;
        return log;
    }

    public static WxMessageLog skipped(String openid, WxMsgType msgType, Long orderId,
                                       String templateId, WxMsgStatus skipReason) {
        WxMessageLog log = new WxMessageLog();
        log.openid = openid;
        log.msgType = msgType;
        log.orderId = orderId;
        log.templateId = templateId != null ? templateId : "";
        log.status = skipReason;
        return log;
    }

    public Long getId() { return id; }
    public String getOpenid() { return openid; }
    public WxMsgType getMsgType() { return msgType; }
    public Long getOrderId() { return orderId; }
    public String getTemplateId() { return templateId; }
    public String getWxMsgId() { return wxMsgId; }
    public WxMsgStatus getStatus() { return status; }
    public String getErrorMsg() { return errorMsg; }
    public LocalDateTime getSentAt() { return sentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
