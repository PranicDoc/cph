package com.pranicdoc.cph.clinic.controller;

import com.pranicdoc.cph.clinic.dto.DashboardSummary;
import com.pranicdoc.cph.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/")
public class DashboardController {

    @GetMapping("summary")
    public ResponseEntity<ApiResponse<DashboardSummary>> getDashboardSummary() {
        return null;
    }
}
