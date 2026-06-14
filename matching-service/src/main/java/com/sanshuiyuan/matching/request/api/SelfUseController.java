package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.auth.CurrentUser;
import com.sanshuiyuan.matching.request.api.dto.SelfUseRequest;
import com.sanshuiyuan.matching.request.api.dto.SelfUseResponse;
import com.sanshuiyuan.matching.request.application.SelfUseUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matching/devices")
public class SelfUseController {

    private final SelfUseUseCase selfUseUseCase;

    public SelfUseController(SelfUseUseCase selfUseUseCase) {
        this.selfUseUseCase = selfUseUseCase;
    }

    @PostMapping("/{id}/self-use")
    public SelfUseResponse selfUse(@PathVariable long id, @RequestBody SelfUseRequest req) {
        return selfUseUseCase.selfUse(CurrentUser.subject(), id, req);
    }
}
