package com.sanshuiyuan.water.session.application;

/** 流量推送事件：iot-gateway 推送的实时流量样本，供 WebSocket 实时下发。 */
public record FlowTickEvent(Long sessionId, long litersMilli, long deltaMilli) {
}
