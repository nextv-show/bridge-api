package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.application.FinanceDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
public class FinanceDashboardController {

    private final FinanceDashboardService service;

    public FinanceDashboardController(FinanceDashboardService service) {
        this.service = service;
    }

    @GetMapping("/finance")
    public Map<String, Object> finance() {
        return service.getOverview();
    }
}
