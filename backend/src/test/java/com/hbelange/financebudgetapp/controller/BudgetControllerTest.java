package com.hbelange.financebudgetapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.BudgetViewDTO;
import com.hbelange.financebudgetapp.service.BudgetService;

import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;

@WebMvcTest(value = BudgetController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BudgetService budgetService;

    private static final UUID CAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void getBudget_returns200WithView() throws Exception {
        BudgetViewDTO dto = new BudgetViewDTO(new BigDecimal("400.00"), List.of());
        when(budgetService.getBudget("2026-05")).thenReturn(dto);

        mockMvc.perform(get("/api/budget").param("month", "2026-05"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readyToAssign").value(400.00))
            .andExpect(jsonPath("$.groups").isArray());
    }

    @Test
    void getBudget_returns400_whenMonthInvalid() throws Exception {
        when(budgetService.getBudget("bad-month"))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month format"));

        mockMvc.perform(get("/api/budget").param("month", "bad-month"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void upsertAllocation_returns204() throws Exception {
        mockMvc.perform(put("/api/budget/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + CAT_ID + "\",\"month\":\"2026-05\",\"assigned\":300.00}"))
            .andExpect(status().isNoContent());

        verify(budgetService).upsertAllocation(any());
    }

    @Test
    void upsertAllocation_returns400_whenBodyInvalid() throws Exception {
        mockMvc.perform(put("/api/budget/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"2026-05\",\"assigned\":300.00}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void upsertAllocation_returns404_whenCategoryMissing() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"))
            .when(budgetService).upsertAllocation(any());

        mockMvc.perform(put("/api/budget/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + CAT_ID + "\",\"month\":\"2026-05\",\"assigned\":300.00}"))
            .andExpect(status().isNotFound());
    }
}
