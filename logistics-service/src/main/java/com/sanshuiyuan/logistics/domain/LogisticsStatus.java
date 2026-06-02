package com.sanshuiyuan.logistics.domain;

/** 物流工单状态机：PENDING_SHIP→SHIPPED→DELIVERED→INSTALLED；CANCELLED 为终态。 */
public enum LogisticsStatus {
    PENDING_SHIP,
    SHIPPED,
    DELIVERED,
    INSTALLED,
    CANCELLED
}
