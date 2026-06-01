package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.auth.CurrentUser;
import com.sanshuiyuan.matching.request.api.dto.CreateRequestBody;
import com.sanshuiyuan.matching.request.api.dto.CreateRequestResponse;
import com.sanshuiyuan.matching.request.api.dto.RequestItem;
import com.sanshuiyuan.matching.request.application.CreateRequestUseCase;
import com.sanshuiyuan.matching.request.application.MyRequestsQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** FR-1/FR-2：发布需求、我的需求、需求详情。 */
@RestController
@RequestMapping("/api/matching/requests")
public class RequestController {

    private final CreateRequestUseCase createUseCase;
    private final MyRequestsQueryService myRequests;

    public RequestController(CreateRequestUseCase createUseCase, MyRequestsQueryService myRequests) {
        this.createUseCase = createUseCase;
        this.myRequests = myRequests;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRequestResponse create(@Valid @RequestBody CreateRequestBody body) {
        return createUseCase.create(CurrentUser.subject(), body);
    }

    @GetMapping("/mine")
    public List<RequestItem> mine(@RequestParam(value = "status", required = false) String status) {
        return myRequests.mine(CurrentUser.subject(), status);
    }

    @GetMapping("/{id}")
    public RequestItem detail(@PathVariable("id") long id) {
        return myRequests.detail(CurrentUser.subject(), id);
    }
}
