package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.auth.CurrentUser;
import com.sanshuiyuan.matching.request.api.dto.ClaimRequestBody;
import com.sanshuiyuan.matching.request.api.dto.ClaimRequestResponse;
import com.sanshuiyuan.matching.request.application.ClaimRequestUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matching/requests")
public class ClaimController {

    private final ClaimRequestUseCase claimRequestUseCase;

    public ClaimController(ClaimRequestUseCase claimRequestUseCase) {
        this.claimRequestUseCase = claimRequestUseCase;
    }

    @PostMapping("/{id}/claim")
    @ResponseStatus(HttpStatus.OK)
    public ClaimRequestResponse claim(@PathVariable("id") long id,
                                      @Valid @RequestBody ClaimRequestBody body) {
        return claimRequestUseCase.claim(CurrentUser.subject(), id, body.deviceAssetId());
    }
}
