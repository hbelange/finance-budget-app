package com.hbelange.financebudgetapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.repository.UserRepository;
import com.hbelange.financebudgetapp.service.TransferService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    private static final UUID FROM_ACCOUNT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TO_ACCOUNT_ID   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID FROM_TX_ID      = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID TO_TX_ID        = UUID.fromString("22222222-0000-0000-0000-000000000002");

    private static final TransactionDTO FROM_DTO = new TransactionDTO(
        FROM_TX_ID, FROM_ACCOUNT_ID, LocalDate.of(2026, 1, 15),
        "Transfer", null, new BigDecimal("-100.00"), null, false, TO_TX_ID
    );
    private static final TransactionDTO TO_DTO = new TransactionDTO(
        TO_TX_ID, TO_ACCOUNT_ID, LocalDate.of(2026, 1, 15),
        "Transfer", null, new BigDecimal("100.00"), null, false, FROM_TX_ID
    );

    private String transferRequestBody() {
        return """
            {
                "fromAccountId": "%s",
                "toAccountId": "%s",
                "date": "2026-01-15",
                "amount": "100.00"
            }
            """.formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID);
    }

    // --- POST /api/transfers ---

    @Test
    void createTransfer_returns201WithBothLegs() throws Exception {
        when(transferService.createTransfer(any(), any())).thenReturn(List.of(FROM_DTO, TO_DTO));

        mockMvc.perform(post("/api/transfers").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferRequestBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].amount").value(-100.00))
            .andExpect(jsonPath("$[1].amount").value(100.00));
    }

    @Test
    void createTransfer_returns400_whenFromAccountIdMissing() throws Exception {
        mockMvc.perform(post("/api/transfers").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "toAccountId": "%s",
                        "date": "2026-01-15",
                        "amount": "100.00"
                    }
                    """.formatted(TO_ACCOUNT_ID)))
            .andExpect(status().isBadRequest());
    }

    // --- PUT /api/transfers/{id} ---

    @Test
    void updateTransfer_returns200WithUpdatedLegs() throws Exception {
        when(transferService.updateTransfer(eq(FROM_TX_ID), any(), any())).thenReturn(List.of(FROM_DTO, TO_DTO));

        mockMvc.perform(put("/api/transfers/" + FROM_TX_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferRequestBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateTransfer_returns404_whenTransferNotFound() throws Exception {
        when(transferService.updateTransfer(eq(FROM_TX_ID), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + FROM_TX_ID));

        mockMvc.perform(put("/api/transfers/" + FROM_TX_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferRequestBody()))
            .andExpect(status().isNotFound());
    }

    // --- DELETE /api/transfers/{id} ---

    @Test
    void deleteTransfer_returns204() throws Exception {
        mockMvc.perform(delete("/api/transfers/" + FROM_TX_ID).with(jwt()))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteTransfer_returns403_whenForbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
            .when(transferService).deleteTransfer(eq(FROM_TX_ID), any());

        mockMvc.perform(delete("/api/transfers/" + FROM_TX_ID).with(jwt()))
            .andExpect(status().isForbidden());
    }
}
