package com.sanshuiyuan.water.session.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.water.auth.H5JwtService;
import com.sanshuiyuan.water.common.H5UserResolver;
import com.sanshuiyuan.water.session.application.FlowTickEvent;
import com.sanshuiyuan.water.session.domain.SessionStatus;
import com.sanshuiyuan.water.session.domain.WaterSession;
import com.sanshuiyuan.water.session.infra.WaterSessionRepository;
import com.sanshuiyuan.water.wallet.infra.ConsumerWalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 出水实时推送 WebSocket 处理器（/ws/water/{sessionId}）。
 * 握手时校验 query param token（H5 JWT）并匹配会话归属；订阅 {@link FlowTickEvent}，
 * 按 1Hz 节流下发 {@code {liters_milli, amount_cents, remaining_balance_cents}}。
 */
@Component
public class WaterRunningWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WaterRunningWebSocketHandler.class);
    private static final long THROTTLE_MILLIS = 1000L; // 1Hz

    private static final String ATTR_SESSION_ID = "waterSessionId";
    private static final String ATTR_LAST_PUSH = "lastPushAt";

    private final H5JwtService jwtService;
    private final H5UserResolver userResolver;
    private final WaterSessionRepository sessionRepo;
    private final ConsumerWalletRepository walletRepo;
    private final ObjectMapper objectMapper;

    /** sessionId → 活跃的 WebSocket 连接集合。 */
    private final Map<Long, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();

    public WaterRunningWebSocketHandler(H5JwtService jwtService, H5UserResolver userResolver,
                                        WaterSessionRepository sessionRepo, ConsumerWalletRepository walletRepo,
                                        ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userResolver = userResolver;
        this.sessionRepo = sessionRepo;
        this.walletRepo = walletRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        Long sessionId = extractSessionId(ws.getUri());
        String token = extractQueryParam(ws.getUri(), "token");
        if (sessionId == null || token == null) {
            ws.close(CloseStatus.BAD_DATA);
            return;
        }
        String openid = jwtService.parseSubject(token);
        if (openid == null) {
            ws.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        Long userId;
        try {
            userId = userResolver.resolveUserId(openid);
        } catch (Exception e) {
            ws.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        WaterSession session = sessionRepo.findByIdAndUserId(sessionId, userId).orElse(null);
        if (session == null) {
            ws.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            ws.close(CloseStatus.NORMAL);
            return;
        }
        ws.getAttributes().put(ATTR_SESSION_ID, sessionId);
        ws.getAttributes().put(ATTR_LAST_PUSH, 0L);
        subscribers.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(ws);
        log.info("[WS] 出水连接建立 sessionId={} userId={}", sessionId, userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        Object sid = ws.getAttributes().get(ATTR_SESSION_ID);
        if (sid instanceof Long sessionId) {
            Set<WebSocketSession> set = subscribers.get(sessionId);
            if (set != null) {
                set.remove(ws);
                if (set.isEmpty()) {
                    subscribers.remove(sessionId);
                }
            }
        }
    }

    /** 收到流量事件，向该会话的订阅者节流推送。 */
    @EventListener
    public void onFlowTick(FlowTickEvent event) {
        Set<WebSocketSession> set = subscribers.get(event.sessionId());
        if (set == null || set.isEmpty()) {
            return;
        }
        WaterSession session = sessionRepo.findById(event.sessionId()).orElse(null);
        if (session == null) {
            return;
        }
        // 会话已结束：通知并关闭
        if (session.getStatus() != SessionStatus.ACTIVE) {
            for (WebSocketSession ws : set) {
                closeQuietly(ws, CloseStatus.NORMAL);
            }
            subscribers.remove(event.sessionId());
            return;
        }

        long amountCents = event.litersMilli() * session.getPricePerLiterCents() / 1000;
        long balance = walletRepo.findByUserId(session.getUserId()).map(w -> w.getBalanceCents()).orElse(0L);
        long remaining = balance - amountCents;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("liters_milli", event.litersMilli());
        payload.put("amount_cents", amountCents);
        payload.put("remaining_balance_cents", remaining);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("[WS] 序列化推送失败 sessionId={}", event.sessionId(), e);
            return;
        }

        long now = System.currentTimeMillis();
        for (WebSocketSession ws : set) {
            try {
                Object last = ws.getAttributes().get(ATTR_LAST_PUSH);
                long lastPush = last instanceof Long l ? l : 0L;
                if (now - lastPush < THROTTLE_MILLIS) {
                    continue; // 1Hz 节流
                }
                if (!ws.isOpen()) {
                    continue;
                }
                ws.sendMessage(new TextMessage(json));
                ws.getAttributes().put(ATTR_LAST_PUSH, now);
            } catch (Exception e) {
                log.warn("[WS] 推送失败 sessionId={}: {}", event.sessionId(), e.getMessage());
            }
        }
    }

    private void closeQuietly(WebSocketSession ws, CloseStatus status) {
        try {
            if (ws.isOpen()) {
                ws.close(status);
            }
        } catch (Exception ignored) {
            // 忽略关闭异常
        }
    }

    private Long extractSessionId(URI uri) {
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        String[] parts = path.split("/");
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractQueryParam(URI uri, String name) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && pair.substring(0, idx).equals(name)) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }
}
