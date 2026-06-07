package com.sanshuiyuan.evidence.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启定时任务（EvidencePollerJob 轮询发件箱）。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
