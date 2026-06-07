package com.sanshuiyuan.water.device.domain;

/** 设备禁用原因。NOT_INSTALLED 未安装；LOCKED 风控/欠费锁定；MANUAL 人工锁定。 */
public enum LockedReason {
    NOT_INSTALLED, LOCKED, MANUAL
}
