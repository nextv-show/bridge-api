package com.sanshuiyuan.water.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启定时任务（超时兜底结算 SettleTimeoutJob）。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
