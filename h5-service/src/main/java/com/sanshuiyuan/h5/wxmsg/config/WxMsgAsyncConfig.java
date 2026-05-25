package com.sanshuiyuan.h5.wxmsg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 微信消息推送专用异步线程池，与主业务线程池隔离。
 * 核心 2 线程，队列 100，拒绝策略 CallerRuns（队满时降级到调用线程，确保不丢失日志记录）。
 */
@Configuration
public class WxMsgAsyncConfig {

    @Bean(name = "wxMsgExecutor")
    public Executor wxMsgExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("wxmsg-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
