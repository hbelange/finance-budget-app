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

import com.hbelange.financebudgetapp.enums.AccountType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.AccountDTO;
import com.hbelange.financebudgetapp.repository.UserRepository;
import com.hbelange.financebudgetapp.service.AccountService;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void getAll_returns200WithAccountList() throws Exception {
        AccountDTO dto = new AccountDTO(ACCOUNT_ID, "Checking", AccountType.CHECKING, new BigDecimal("100.00"));
        when(accountService.findAll(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/accounts").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(ACCOUNT_ID.toString()))
            .andExpect(jsonPath("$[0].name").value("Checking"))
            .andExpect(jsonPath("$[0].type").value("CHECKING"))
            .andExpect(jsonPath("$[0].balance").value(100.00));
    }

    @Test
    void create_returns201WithDto() throws Exception {
        AccountDTO dto = new AccountDTO(ACCOUNT_ID, "Savings", AccountType.SAVINGS, BigDecimal.ZERO);
        when(accountService.create(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/accounts").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Savings\",\"type\":\"SAVINGS\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
            .andExpect(jsonPath("$.name").value("Savings"));
    }

    @Test
    void create_returns400_whenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/accounts").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"type\":\"SAVINGS\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_whenTypeIsMissing() throws Exception {
        mockMvc.perform(post("/api/accounts").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Savings\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns200WithDto() throws Exception {
        AccountDTO dto = new AccountDTO(ACCOUNT_ID, "Updated", AccountType.CHECKING, new BigDecimal("50.00"));
        when(accountService.update(eq(ACCOUNT_ID), any(), any())).thenReturn(dto);

        mockMvc.perform(put("/api/accounts/" + ACCOUNT_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated\",\"type\":\"CHECKING\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void update_returns404_whenAccountMissing() throws Exception {
        when(accountService.update(eq(ACCOUNT_ID), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/accounts/" + ACCOUNT_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"type\":\"CHECKING\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/accounts/" + ACCOUNT_ID).with(jwt()))
            .andExpect(status().isNoContent());

        verify(accountService).delete(eq(ACCOUNT_ID), any());
    }

    @Test
    void delete_returns409_whenTransactionsExist() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Account has existing transactions"))
            .when(accountService).delete(eq(ACCOUNT_ID), any());

        mockMvc.perform(delete("/api/accounts/" + ACCOUNT_ID).with(jwt()))
            .andExpect(status().isConflict());
    }

    @Test
    void delete_returns404_whenAccountMissing() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
            .when(accountService).delete(eq(ACCOUNT_ID), any());

        mockMvc.perform(delete("/api/accounts/" + ACCOUNT_ID).with(jwt()))
            .andExpect(status().isNotFound());
    }
}
