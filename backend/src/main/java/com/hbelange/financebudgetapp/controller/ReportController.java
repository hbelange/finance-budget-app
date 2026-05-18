package com.hbelange.financebudgetapp.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hbelange.financebudgetapp.dto.DashboardDto;
import com.hbelange.financebudgetapp.dto.SpendingByCategoryDto;
import com.hbelange.financebudgetapp.service.ReportService;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    @Autowired
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public DashboardDto getDashboard(@RequestParam String month, @AuthenticationPrincipal Jwt jwt) {
        return reportService.getDashboard(month, jwt.getSubject());
    }

    @GetMapping("/spending-by-category")
    public List<SpendingByCategoryDto> getSpendingByCategory(@RequestParam String month, @AuthenticationPrincipal Jwt jwt) {
        return reportService.getSpendingByCategory(month, jwt.getSubject());
    }
}
