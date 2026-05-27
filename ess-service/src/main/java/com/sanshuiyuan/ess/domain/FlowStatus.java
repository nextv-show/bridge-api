package com.sanshuiyuan.ess.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 签署流程状态枚举。
 */
public enum FlowStatus {
    /** 初始化 */
    INIT,
    /** 流程已创建 */
    CREATED,
    /** 签署中 */
    SIGNING,
    /** 已完成 */
    COMPLETED,
    /** 已取消 */
    CANCELLED,
    /** 已拒绝 */
    REJECTED,
    /** 已过期 */
    EXPIRED,
    /** 异常 */
    ERROR
}
