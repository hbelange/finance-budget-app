package com.hbelange.financebudgetapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.hbelange.financebudgetapp.enums.AccountType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.AccountDTO;
import com.hbelange.financebudgetapp.service.AccountService;

import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;

@WebMvcTest(value = AccountController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void getAll_returns200WithAccountList() throws Exception {
        AccountDTO dto = new AccountDTO(ACCOUNT_ID, "Checking", AccountType.CHECKING, new BigDecimal("100.00"));
        when(accountService.findAll()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/accounts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(ACCOUNT_ID.toString()))
            .andExpect(jsonPath("$[0].name").value("Checking"))
            .andExpect(jsonPath("$[0].type").value("CHECKING"))
            .andExpect(jsonPath("$[0].balance").value(100.00));
    }

    @Test
    void create_returns201WithDto() throws Exception {
        AccountDTO dto = new AccountDTO(ACCOUNT_ID, "Savings", AccountType.SAVINGS, BigDecimal.ZERO);
        when(accountService.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Savings\",\"type\":\"SAVINGS\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
            .andExpect(jsonPath("$.name").value("Savings"));
    }

    @Test
    void create_returns400_whenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"type\":\"SAVINGS\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_whenTypeIsMissing() throws Exception {
        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Savings\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns200WithDto() throws Exception {
        AccountDTO dto = new AccountDTO(ACCOUNT_ID, "Updated", AccountType.CHECKING, new BigDecimal("50.00"));
        when(accountService.update(eq(ACCOUNT_ID), any())).thenReturn(dto);

        mockMvc.perform(put("/api/accounts/" + ACCOUNT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated\",\"type\":\"CHECKING\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void update_returns404_whenAccountMissing() throws Exception {
        when(accountService.update(eq(ACCOUNT_ID), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/accounts/" + ACCOUNT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"type\":\"CHECKING\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/accounts/" + ACCOUNT_ID))
            .andExpect(status().isNoContent());

        verify(accountService).delete(ACCOUNT_ID);
    }

    @Test
    void delete_returns409_whenTransactionsExist() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Account has existing transactions"))
            .when(accountService).delete(ACCOUNT_ID);

        mockMvc.perform(delete("/api/accounts/" + ACCOUNT_ID))
            .andExpect(status().isConflict());
    }

    @Test
    void delete_returns404_whenAccountMissing() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
            .when(accountService).delete(ACCOUNT_ID);

        mockMvc.perform(delete("/api/accounts/" + ACCOUNT_ID))
            .andExpect(status().isNotFound());
    }
}
