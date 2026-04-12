package com.hbelange.financebudgetapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final TransactionDTO SAMPLE_DTO = new TransactionDTO(
        TRANSACTION_ID, ACCOUNT_ID, LocalDate.of(2026, 1, 15),
        "Grocery Store", null, new BigDecimal("50.00"), null, false
    );

    // --- POST /api/transactions ---

    @Test
    void createTransaction_returns200WithDto() throws Exception {
        when(transactionService.create(any())).thenReturn(SAMPLE_DTO);

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "accountId": "%s",
                        "date": "2026-01-15",
                        "amount": "50.00"
                    }
                    """.formatted(ACCOUNT_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
            .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID.toString()))
            .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    void createTransaction_returns400_whenAccountIdMissing() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "date": "2026-01-15",
                        "amount": "50.00"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_returns400_whenDateMissing() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "accountId": "%s",
                        "amount": "50.00"
                    }
                    """.formatted(ACCOUNT_ID)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_returns400_whenAmountMissing() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "accountId": "%s",
                        "date": "2026-01-15"
                    }
                    """.formatted(ACCOUNT_ID)))
            .andExpect(status().isBadRequest());
    }

    // --- PUT /api/transactions/{id} ---

    @Test
    void updateTransaction_returns200WithDto() throws Exception {
        when(transactionService.update(eq(TRANSACTION_ID), any())).thenReturn(SAMPLE_DTO);

        mockMvc.perform(put("/api/transactions/" + TRANSACTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "accountId": "%s",
                        "date": "2026-01-15",
                        "amount": "50.00"
                    }
                    """.formatted(ACCOUNT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
            .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID.toString()));
    }

    @Test
    void updateTransaction_returns404_whenTransactionNotFound() throws Exception {
        when(transactionService.update(eq(TRANSACTION_ID), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + TRANSACTION_ID));

        mockMvc.perform(put("/api/transactions/" + TRANSACTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "accountId": "%s",
                        "date": "2026-01-15",
                        "amount": "50.00"
                    }
                    """.formatted(ACCOUNT_ID)))
            .andExpect(status().isNotFound());
    }

    // --- GET /api/transactions ---

    @Test
    void getTransactions_returns200_withNoFilters() throws Exception {
        when(transactionService.findAll(isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(SAMPLE_DTO)));

        mockMvc.perform(get("/api/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(TRANSACTION_ID.toString()))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getTransactions_returns200_withAccountIdFilter() throws Exception {
        when(transactionService.findAll(eq(ACCOUNT_ID), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(SAMPLE_DTO)));

        mockMvc.perform(get("/api/transactions")
                .param("accountId", ACCOUNT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].accountId").value(ACCOUNT_ID.toString()));
    }

    @Test
    void getTransactions_returns200_withMonthFilter() throws Exception {
        when(transactionService.findAll(isNull(), eq(YearMonth.of(2026, 1)), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(SAMPLE_DTO)));

        mockMvc.perform(get("/api/transactions")
                .param("month", "2026-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].date").value("2026-01-15"));
    }

    @Test
    void getTransactions_returns200_withAccountIdAndMonthFilter() throws Exception {
        when(transactionService.findAll(eq(ACCOUNT_ID), eq(YearMonth.of(2026, 1)), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(SAMPLE_DTO)));

        mockMvc.perform(get("/api/transactions")
                .param("accountId", ACCOUNT_ID.toString())
                .param("month", "2026-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].accountId").value(ACCOUNT_ID.toString()))
            .andExpect(jsonPath("$.content[0].date").value("2026-01-15"));
    }

    // --- DELETE /api/transactions/{id} ---

    @Test
    void deleteTransaction_returns204() throws Exception {
        mockMvc.perform(delete("/api/transactions/" + TRANSACTION_ID))
            .andExpect(status().isNoContent());
    }
}
