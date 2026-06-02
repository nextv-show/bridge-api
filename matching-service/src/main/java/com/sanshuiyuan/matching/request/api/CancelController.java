package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.auth.CurrentUser;
import com.sanshuiyuan.matching.request.api.dto.CancelResponse;
import com.sanshuiyuan.matching.request.application.CancelRequestUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-5：联系人取消需求。 */
@RestController
@RequestMapping("/api/matching/requests")
public class CancelController {

    private final CancelRequestUseCase cancelRequestUseCase;

    public CancelController(CancelRequestUseCase cancelRequestUseCase) {
        this.cancelRequestUseCase = cancelRequestUseCase;
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public CancelResponse cancel(@PathVariable("id") long id) {
        return cancelRequestUseCase.cancel(CurrentUser.subject(), id);
    }
}
