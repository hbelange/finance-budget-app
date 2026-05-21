package com.hbelange.financebudgetapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.DashboardDto;
import com.hbelange.financebudgetapp.dto.SpendingByCategoryDto;
import com.hbelange.financebudgetapp.repository.UserRepository;
import com.hbelange.financebudgetapp.service.ReportService;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    private static final UUID CAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void getDashboard_returns200WithTotals() throws Exception {
        DashboardDto dto = new DashboardDto(
                new BigDecimal("10000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("1500.00"));
        when(reportService.getDashboard(eq("2026-05"), any())).thenReturn(dto);

        mockMvc.perform(get("/api/reports/dashboard").with(jwt()).param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netWorth").value(10000.00))
                .andExpect(jsonPath("$.incomeThisMonth").value(3000.00))
                .andExpect(jsonPath("$.spentThisMonth").value(1500.00));
    }

    @Test
    void getDashboard_returns400_whenMonthInvalid() throws Exception {
        when(reportService.getDashboard(eq("bad-month"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month format"));

        mockMvc.perform(get("/api/reports/dashboard").with(jwt()).param("month", "bad-month"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSpendingByCategory_returns200WithList() throws Exception {
        List<SpendingByCategoryDto> result = List.of(
                new SpendingByCategoryDto(CAT_ID, "Rent", new BigDecimal("1200.00")));
        when(reportService.getSpendingByCategory(eq("2026-05"), any())).thenReturn(result);

        mockMvc.perform(get("/api/reports/spending-by-category").with(jwt()).param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(CAT_ID.toString()))
                .andExpect(jsonPath("$[0].categoryName").value("Rent"))
                .andExpect(jsonPath("$[0].spent").value(1200.00));
    }

    @Test
    void getSpendingByCategory_returns200WithEmptyList_whenNoSpending() throws Exception {
        when(reportService.getSpendingByCategory(eq("2026-05"), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/reports/spending-by-category").with(jwt()).param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getSpendingByCategory_returns400_whenMonthInvalid() throws Exception {
        when(reportService.getSpendingByCategory(eq("bad-month"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month format"));

        mockMvc.perform(get("/api/reports/spending-by-category").with(jwt()).param("month", "bad-month"))
                .andExpect(status().isBadRequest());
    }
}
