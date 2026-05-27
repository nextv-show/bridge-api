package com.sanshuiyuan.h5.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class H5RealtimeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(H5RealtimeBroadcaster.class);
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter open(String openid) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(openid, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(openid, emitter));
        emitter.onTimeout(() -> remove(openid, emitter));
        emitter.onError(ex -> remove(openid, emitter));

        try {
            emitter.send(SseEmitter.event().name("ping").data("ok"));
        } catch (IOException e) {
            remove(openid, emitter);
        }
        return emitter;
    }

    public void publish(String openid, H5RealtimeEvent event) {
        List<SseEmitter> list = emitters.get(openid);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event.type()).data(event));
            } catch (Exception e) {
                log.debug("SSE 推送失败 openid={} type={}", openid, event.type(), e);
                remove(openid, emitter);
            }
        }
    }

    private void remove(String openid, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(openid);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(openid, list);
        }
    }
}
