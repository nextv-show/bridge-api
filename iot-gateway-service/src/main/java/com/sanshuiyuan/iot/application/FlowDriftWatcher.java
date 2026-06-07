package com.sanshuiyuan.iot.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 流量漂移看护（stub）。后续将比对 device_telemetry_flow 累计量与设备自报滤芯寿命，
 * 偏差 > ±5% → 升 FILTER_DRIFT 告警。完整实现待 Phase D（water_sessions 就绪）/F。
 */
@Service
public class FlowDriftWatcher {

    private static final Logger log = LoggerFactory.getLogger(FlowDriftWatcher.class);

    // Stub: will compare device_telemetry_flow cumulative vs device self-reported filter life
    // 偏差 > ±5% → FILTER_DRIFT alarm
    // Full implementation deferred to Phase D/F
}
