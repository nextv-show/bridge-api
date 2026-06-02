package com.sanshuiyuan.cend.realtime;

import com.sanshuiyuan.cend.auth.H5JwtService;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * H5 实时推送入口：EventSource + Authorization Bearer token。
 */
@RestController
@RequestMapping("/api/h5/realtime")
public class H5RealtimeController {

    private final H5JwtService jwtService;
    private final H5RealtimeBroadcaster broadcaster;

    public H5RealtimeController(H5JwtService jwtService, H5RealtimeBroadcaster broadcaster) {
        this.jwtService = jwtService;
        this.broadcaster = broadcaster;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        String bearer = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        String openid = bearer != null ? jwtService.parseOpenid(bearer) : null;
        if (openid == null && token != null && !token.isBlank()) {
            openid = jwtService.parseOpenid(token);
        }
        if (openid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return broadcaster.open(openid);
    }
}
