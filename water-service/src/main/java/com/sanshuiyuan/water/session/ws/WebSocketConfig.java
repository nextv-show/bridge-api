package com.sanshuiyuan.water.session.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 注册：出水实时推送 {@code /ws/water/{sessionId}}。鉴权在握手时由 handler 校验 token。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WaterRunningWebSocketHandler waterRunningHandler;

    public WebSocketConfig(WaterRunningWebSocketHandler waterRunningHandler) {
        this.waterRunningHandler = waterRunningHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(waterRunningHandler, "/ws/water/{sessionId}")
                .setAllowedOriginPatterns("*");
    }
}
