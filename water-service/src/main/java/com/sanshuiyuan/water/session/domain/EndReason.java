package com.sanshuiyuan.water.session.domain;

/** 会话结束原因：用户主动停、余额耗尽、设备限额、超时兜底、设备/系统异常。 */
public enum EndReason {
    USER_STOP, BALANCE_OUT, DEVICE_LIMIT, TIMEOUT, ERROR
}
