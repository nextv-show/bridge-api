package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.auth.CurrentUser;
import com.sanshuiyuan.matching.request.api.dto.ConfirmResponse;
import com.sanshuiyuan.matching.request.application.ConfirmClaimUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** P1-2：锁定方确认推进接单。 */
@RestController
@RequestMapping("/api/matching/requests")
public class ConfirmController {

    private final ConfirmClaimUseCase confirmClaimUseCase;

    public ConfirmController(ConfirmClaimUseCase confirmClaimUseCase) {
        this.confirmClaimUseCase = confirmClaimUseCase;
    }

    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.OK)
    public ConfirmResponse confirm(@PathVariable("id") long id) {
        return confirmClaimUseCase.confirm(CurrentUser.subject(), id);
    }
}
