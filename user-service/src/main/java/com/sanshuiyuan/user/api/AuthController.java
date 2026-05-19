package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.api.dto.AuthResponse;
import com.sanshuiyuan.user.api.dto.LoginRequest;
import com.sanshuiyuan.user.api.dto.RefreshRequest;
import com.sanshuiyuan.user.api.dto.TokenResponse;
import com.sanshuiyuan.user.application.WxLoginUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final WxLoginUseCase wxLoginUseCase;

    public AuthController(WxLoginUseCase wxLoginUseCase) {
        this.wxLoginUseCase = wxLoginUseCase;
    }

    @PostMapping("/wx/miniprogram")
    public ResponseEntity<AuthResponse> loginMiniProgram(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(wxLoginUseCase.loginMiniProgram(request.getJsCode()));
    }

    @PostMapping("/wx/app")
    public ResponseEntity<AuthResponse> loginApp(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(wxLoginUseCase.loginApp(request.getWxAuthCode()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(wxLoginUseCase.refreshToken(request.getRefreshToken()));
    }
}
