package com.sanshuiyuan.water.session.domain;

/** 取水会话状态：ACTIVE 出水中，CLOSED 已结算，ABORTED 异常中止（如下发失败）。 */
public enum SessionStatus {
    ACTIVE, CLOSED, ABORTED
}
