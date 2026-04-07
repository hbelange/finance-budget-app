package com.hbelange.financebudgetapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hbelange.financebudgetapp.service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Hint: @WebMvcTest spins up only the web layer (controller + MockMvc), not the full app.
// The real TransactionService is replaced by a @MockitoBean, so you control what it returns.
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Hint: @MockitoBean registers a Mockito mock as a Spring bean so the controller can inject it.
    @MockitoBean
    private TransactionService transactionService;

    // Fixed IDs make test output easier to read
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // --- POST /api/transactions ---

    @Test
    void createTransaction_returns200WithDto() throws Exception {
        // Hint: build a TransactionDTO to return from the mocked service.
        // Use when(transactionService.create(any())).thenReturn(dto).
        // Perform a POST to "/api/transactions" with JSON body containing accountId, date, and amount.
        // Assert status().isOk() and jsonPath("$.id").value(TRANSACTION_ID.toString()).
        // Hint: dates in JSON should be "yyyy-MM-dd" strings, e.g. "2026-01-15".
    }

    @Test
    void createTransaction_returns400_whenAccountIdMissing() throws Exception {
        // Hint: omit "accountId" from the JSON body.
        // @NotNull on TransactionRequest.accountId means validation should reject it.
        // Assert status().isBadRequest() — no mock setup needed, it never reaches the service.
    }

    @Test
    void createTransaction_returns400_whenDateMissing() throws Exception {
        // Hint: omit "date" from the JSON body.
        // Similar to above — @NotNull on date triggers a 400.
    }

    @Test
    void createTransaction_returns400_whenAmountMissing() throws Exception {
        // Hint: omit "amount" from the JSON body.
        // @NotNull on amount should cause a 400.
    }

    // --- PUT /api/transactions/{id} ---

    @Test
    void updateTransaction_returns200WithDto() throws Exception {
        // Hint: build a TransactionDTO, stub transactionService.update(eq(TRANSACTION_ID), any()).
        // Perform a PUT to "/api/transactions/" + TRANSACTION_ID.
        // Assert the response body has the expected field values.
    }

    @Test
    void updateTransaction_returns500_whenTransactionNotFound() throws Exception {
        // Hint: the TransactionService throws IllegalArgumentException (not ResponseStatusException).
        // Unlike AccountService, there is no @ControllerAdvice mapping it to 404.
        // So the controller returns 500 by default when an IllegalArgumentException is thrown.
        // Stub transactionService.update to throw new IllegalArgumentException("Transaction not found: ...").
        // Assert status().isInternalServerError().
        //
        // Follow-up to think about: would it be better to return 404 here?
        // If so, you could add a @ControllerAdvice that maps IllegalArgumentException -> 404.
    }

    // --- GET /api/transactions ---

    @Test
    void getTransactions_returns200_withNoFilters() throws Exception {
        // Hint: the endpoint returns Page<TransactionDTO>, which serializes to a JSON object
        // with fields: "content" (array), "totalElements", "totalPages", "size", "number".
        // Stub transactionService.findAll(null, null, any()) to return a PageImpl containing one DTO.
        // Hint: Pageable is passed as the third argument — use any(Pageable.class) in the stub.
        // Perform GET "/api/transactions".
        // Assert jsonPath("$.content[0].id").value(TRANSACTION_ID.toString()).
    }

    @Test
    void getTransactions_returns200_withAccountIdFilter() throws Exception {
        // Hint: add a request param: .param("accountId", ACCOUNT_ID.toString()).
        // Stub transactionService.findAll(eq(ACCOUNT_ID), isNull(), any()) to return a Page.
        // Hint: import static org.mockito.ArgumentMatchers.isNull to match the null month param.
    }

    @Test
    void getTransactions_returns200_withMonthFilter() throws Exception {
        // Hint: add .param("month", "2026-01") to the GET request.
        // The controller uses @DateTimeFormat(pattern = "yyyy-MM") to parse it as YearMonth.
        // Stub transactionService.findAll(isNull(), eq(YearMonth.of(2026, 1)), any()).
    }

    @Test
    void getTransactions_returns200_withAccountIdAndMonthFilter() throws Exception {
        // Hint: combine both params: .param("accountId", ...).param("month", "2026-01").
        // Stub with both non-null in the matcher.
    }
}
