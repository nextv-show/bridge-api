package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.auth.CurrentUser;
import com.sanshuiyuan.matching.request.api.dto.ReleaseResponse;
import com.sanshuiyuan.matching.request.application.ReleaseRequestUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-5：锁定方释放需求。 */
@RestController
@RequestMapping("/api/matching/requests")
public class ReleaseController {

    private final ReleaseRequestUseCase releaseRequestUseCase;

    public ReleaseController(ReleaseRequestUseCase releaseRequestUseCase) {
        this.releaseRequestUseCase = releaseRequestUseCase;
    }

    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.OK)
    public ReleaseResponse release(@PathVariable("id") long id) {
        return releaseRequestUseCase.release(CurrentUser.subject(), id);
    }
}
