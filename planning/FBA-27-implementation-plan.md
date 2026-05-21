# FBA-27 Transfers — Remaining Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Write backend tests for TransferService/TransferController and wire up the Angular transfer dialog and ledger integration.

**Architecture:** Backend is complete. Remaining work is tests for the two new backend classes, then three frontend additions: a TransferService, a TransferDialogComponent, and updates to TransactionLedgerComponent.

**Tech Stack:** Java 21 / Spring Boot / Mockito / MockMvc (backend); Angular 21 standalone / Angular Material / RxJS (frontend)

---

## File Map

| Action | Path |
|--------|------|
| New | `backend/src/test/java/com/hbelange/financebudgetapp/service/TransferServiceTest.java` |
| Modify | `backend/src/test/java/com/hbelange/financebudgetapp/service/TransactionServiceTest.java` |
| New | `backend/src/test/java/com/hbelange/financebudgetapp/controller/TransferControllerTest.java` |
| Modify | `backend/src/test/java/com/hbelange/financebudgetapp/controller/TransactionControllerTest.java` |
| Modify | `frontend/src/app/core/services/transaction.service.ts` |
| New | `frontend/src/app/core/services/transfer.service.ts` |
| New | `frontend/src/app/transactions/transfer-dialog.component.ts` |
| Modify | `frontend/src/app/transactions/transaction-ledger.component.ts` |
| Modify | `frontend/src/app/transactions/transaction-ledger.component.html` |

---

## Task 1: TransferService unit tests

**Files:**
- Create: `backend/src/test/java/com/hbelange/financebudgetapp/service/TransferServiceTest.java`

- [ ] **Step 1: Write the failing test class**

```java
package com.hbelange.financebudgetapp.service;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.dto.TransferRequest;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransferService transferService;

    private static final String USER_SUB = "auth0|test-user";
    private static final String OTHER_SUB = "auth0|other-user";

    private static final UUID FROM_ACCOUNT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TO_ACCOUNT_ID   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID FROM_TX_ID      = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID TO_TX_ID        = UUID.fromString("22222222-0000-0000-0000-000000000002");

    private Account fromAccount;
    private Account toAccount;
    private TransferRequest req;

    @BeforeEach
    void setUp() {
        fromAccount = new Account();
        fromAccount.setId(FROM_ACCOUNT_ID);
        fromAccount.setUserSub(USER_SUB);

        toAccount = new Account();
        toAccount.setId(TO_ACCOUNT_ID);
        toAccount.setUserSub(USER_SUB);

        req = new TransferRequest(FROM_ACCOUNT_ID, TO_ACCOUNT_ID,
                LocalDate.of(2026, 1, 15), new BigDecimal("100.00"), "Transfer memo", false);
    }

    // --- createTransfer ---

    @Test
    void createTransfer_returnsTwoLinkedDtos() {
        Transaction toSaved  = buildTx(TO_TX_ID,   toAccount,   new BigDecimal("100.00"),  null);
        Transaction fromSaved = buildTx(FROM_TX_ID, fromAccount, new BigDecimal("-100.00"), TO_TX_ID);
        Transaction toFinal  = buildTx(TO_TX_ID,   toAccount,   new BigDecimal("100.00"),  FROM_TX_ID);

        when(accountRepository.findById(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));
        when(accountRepository.findById(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));
        when(transactionRepository.save(any())).thenReturn(toSaved, fromSaved, toFinal);

        List<TransactionDTO> result = transferService.createTransfer(req, USER_SUB);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(result.get(1).amount()).isEqualByComparingTo("-100.00");
        assertThat(result.get(0).transferId()).isEqualTo(FROM_TX_ID);
        assertThat(result.get(1).transferId()).isEqualTo(TO_TX_ID);
        verify(transactionRepository, times(3)).save(any());
    }

    @Test
    void createTransfer_throwsForbidden_whenFromAccountBelongsToOtherUser() {
        fromAccount.setUserSub(OTHER_SUB);
        when(accountRepository.findById(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));
        when(accountRepository.findById(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));

        assertThatThrownBy(() -> transferService.createTransfer(req, USER_SUB))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createTransfer_throwsForbidden_whenToAccountBelongsToOtherUser() {
        toAccount.setUserSub(OTHER_SUB);
        when(accountRepository.findById(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));
        when(accountRepository.findById(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));

        assertThatThrownBy(() -> transferService.createTransfer(req, USER_SUB))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- deleteTransfer ---

    @Test
    void deleteTransfer_deletesBothLegs() {
        Transaction toTx   = buildTx(TO_TX_ID,   toAccount,   new BigDecimal("100.00"),  FROM_TX_ID);
        Transaction fromTx = buildTx(FROM_TX_ID, fromAccount, new BigDecimal("-100.00"), TO_TX_ID);

        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(toTx));
        when(transactionRepository.findById(FROM_TX_ID)).thenReturn(Optional.of(fromTx));

        transferService.deleteTransfer(TO_TX_ID, USER_SUB);

        verify(transactionRepository).delete(toTx);
        verify(transactionRepository).delete(fromTx);
    }

    @Test
    void deleteTransfer_throwsForbidden_whenTransactionBelongsToOtherUser() {
        Transaction toTx = buildTx(TO_TX_ID, toAccount, new BigDecimal("100.00"), FROM_TX_ID);
        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(toTx));

        assertThatThrownBy(() -> transferService.deleteTransfer(TO_TX_ID, OTHER_SUB))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    @Test
    void deleteTransfer_throwsBadRequest_whenNotATransferLeg() {
        Transaction normalTx = buildTx(TO_TX_ID, toAccount, new BigDecimal("100.00"), null);
        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(normalTx));

        assertThatThrownBy(() -> transferService.deleteTransfer(TO_TX_ID, USER_SUB))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- updateTransfer ---

    @Test
    void updateTransfer_syncsDateAmountMemoCleared() {
        // toTx has positive amount → it's the "to" leg
        Transaction toTx   = buildTx(TO_TX_ID,   toAccount,   new BigDecimal("100.00"),  FROM_TX_ID);
        Transaction fromTx = buildTx(FROM_TX_ID, fromAccount, new BigDecimal("-100.00"), TO_TX_ID);

        TransferRequest updateReq = new TransferRequest(FROM_ACCOUNT_ID, TO_ACCOUNT_ID,
                LocalDate.of(2026, 2, 1), new BigDecimal("200.00"), "updated", true);

        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(toTx));
        when(transactionRepository.findById(FROM_TX_ID)).thenReturn(Optional.of(fromTx));
        when(transactionRepository.save(any())).thenReturn(toTx, fromTx);

        List<TransactionDTO> result = transferService.updateTransfer(TO_TX_ID, updateReq, USER_SUB);

        assertThat(toTx.getAmount()).isEqualByComparingTo("200.00");
        assertThat(fromTx.getAmount()).isEqualByComparingTo("-200.00");
        assertThat(toTx.getDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(toTx.getMemo()).isEqualTo("updated");
        assertThat(toTx.getCleared()).isTrue();
        assertThat(result).hasSize(2);
    }

    @Test
    void updateTransfer_throwsForbidden_whenTransactionBelongsToOtherUser() {
        Transaction toTx = buildTx(TO_TX_ID, toAccount, new BigDecimal("100.00"), FROM_TX_ID);
        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(toTx));

        assertThatThrownBy(() -> transferService.updateTransfer(TO_TX_ID, req, OTHER_SUB))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- helper ---

    private Transaction buildTx(UUID id, Account account, BigDecimal amount, UUID transferId) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setAccount(account);
        t.setDate(LocalDate.of(2026, 1, 15));
        t.setAmount(amount);
        t.setPayee("Transfer");
        t.setCleared(false);
        t.setTransferId(transferId);
        return t;
    }
}
```

- [ ] **Step 2: Run the tests to confirm they pass**

```bash
cd /Users/harrisonbelanger/Desktop/Harrison/Projects/finance-budget-app
./mvnw test -Dtest=TransferServiceTest -pl backend
```

Expected: `BUILD SUCCESS` with all tests passing.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/hbelange/financebudgetapp/service/TransferServiceTest.java
git commit -m "test: add TransferService unit tests"
```

---

## Task 2: TransactionServiceTest + TransactionControllerTest transfer guard tests

**Files:**
- Modify: `backend/src/test/java/com/hbelange/financebudgetapp/service/TransactionServiceTest.java`
- Modify: `backend/src/test/java/com/hbelange/financebudgetapp/controller/TransactionControllerTest.java`

- [ ] **Step 1: Add 409 test to TransactionServiceTest**

In `TransactionServiceTest.java`, after the existing `delete_throwsNotFound_whenTransactionMissing` test, add:

```java
@Test
void delete_throwsConflict_whenTransactionIsTransferLeg() {
    UUID txId = txA_jan.getId();
    txA_jan.setTransferId(UUID.randomUUID());
    when(transactionRepository.findById(txId)).thenReturn(Optional.of(txA_jan));

    assertThatThrownBy(() -> transactionService.delete(txId, USER_SUB))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    verify(transactionRepository, never()).deleteById(any());
}
```

- [ ] **Step 2: Add 409 test to TransactionControllerTest**

In `TransactionControllerTest.java`, add this import at the top of the import block:

```java
import static org.mockito.Mockito.doThrow;
```

Then add the test after `deleteTransaction_returns204`:

```java
@Test
void deleteTransaction_returns409_whenTransactionIsTransferLeg() throws Exception {
    doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Use DELETE /api/transfers/{id} to delete a transfer"))
        .when(transactionService).delete(eq(TRANSACTION_ID), any());

    mockMvc.perform(delete("/api/transactions/" + TRANSACTION_ID).with(jwt()))
        .andExpect(status().isConflict());
}
```

- [ ] **Step 3: Run the tests**

```bash
./mvnw test -Dtest=TransactionServiceTest,TransactionControllerTest -pl backend
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/hbelange/financebudgetapp/service/TransactionServiceTest.java
git add backend/src/test/java/com/hbelange/financebudgetapp/controller/TransactionControllerTest.java
git commit -m "test: add transfer-leg 409 guard tests to TransactionService and TransactionController"
```

---

## Task 3: TransferControllerTest

**Files:**
- Create: `backend/src/test/java/com/hbelange/financebudgetapp/controller/TransferControllerTest.java`

- [ ] **Step 1: Write the test class**

```java
package com.hbelange.financebudgetapp.controller;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

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
        when(transferService.createTransfer(any(), any())).thenReturn(List.of(TO_DTO, FROM_DTO));

        mockMvc.perform(post("/api/transfers").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferRequestBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].amount").value(100.00))
            .andExpect(jsonPath("$[1].amount").value(-100.00));
    }

    @Test
    void createTransfer_returns400_whenFromAccountIdMissing() throws Exception {
        mockMvc.perform(post("/api/transfers").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "toAccountId": "%s", "date": "2026-01-15", "amount": "100.00" }
                    """.formatted(TO_ACCOUNT_ID)))
            .andExpect(status().isBadRequest());
    }

    // --- PUT /api/transfers/{id} ---

    @Test
    void updateTransfer_returns200WithUpdatedLegs() throws Exception {
        when(transferService.updateTransfer(eq(FROM_TX_ID), any(), any()))
            .thenReturn(List.of(TO_DTO, FROM_DTO));

        mockMvc.perform(put("/api/transfers/" + FROM_TX_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferRequestBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateTransfer_returns404_whenTransferNotFound() throws Exception {
        when(transferService.updateTransfer(eq(FROM_TX_ID), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

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
```

- [ ] **Step 2: Run the tests**

```bash
./mvnw test -Dtest=TransferControllerTest -pl backend
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run the full test suite to confirm no regressions**

```bash
./mvnw test -pl backend
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/hbelange/financebudgetapp/controller/TransferControllerTest.java
git commit -m "test: add TransferController integration tests"
```

---

## Task 4: Frontend — Transaction interface + TransferService

**Files:**
- Modify: `frontend/src/app/core/services/transaction.service.ts`
- Create: `frontend/src/app/core/services/transfer.service.ts`

- [ ] **Step 1: Add `transferId` to the `Transaction` interface**

In `transaction.service.ts`, change the `Transaction` interface from:

```typescript
export interface Transaction {
  id: string;
  accountId: string;
  date: string; // yyyy-MM-dd
  payee: string | null;
  categoryId: string | null;
  amount: number;
  memo: string | null;
  cleared: boolean;
}
```

To:

```typescript
export interface Transaction {
  id: string;
  accountId: string;
  date: string; // yyyy-MM-dd
  payee: string | null;
  categoryId: string | null;
  amount: number;
  memo: string | null;
  cleared: boolean;
  transferId: string | null;
}
```

- [ ] **Step 2: Create TransferService**

Create `frontend/src/app/core/services/transfer.service.ts`:

```typescript
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Transaction } from './transaction.service';

export interface TransferRequest {
  fromAccountId: string;
  toAccountId: string;
  date: string; // yyyy-MM-dd
  amount: number;
  memo: string | null;
  cleared: boolean;
}

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);

  createTransfer(req: TransferRequest): Observable<Transaction[]> {
    return this.http.post<Transaction[]>('/api/transfers', req);
  }

  updateTransfer(id: string, req: TransferRequest): Observable<Transaction[]> {
    return this.http.put<Transaction[]>(`/api/transfers/${id}`, req);
  }

  deleteTransfer(id: string): Observable<void> {
    return this.http.delete<void>(`/api/transfers/${id}`);
  }
}
```

- [ ] **Step 3: Verify no TypeScript errors**

```bash
cd /Users/harrisonbelanger/Desktop/Harrison/Projects/finance-budget-app/frontend
npx ng build --no-progress 2>&1 | tail -20
```

Expected: no errors. If `transferId` is used in existing code, TypeScript will surface it.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/core/services/transaction.service.ts
git add frontend/src/app/core/services/transfer.service.ts
git commit -m "feat: add transferId to Transaction interface and create TransferService"
```

---

## Task 5: TransferDialogComponent

**Files:**
- Create: `frontend/src/app/transactions/transfer-dialog.component.ts`

The dialog receives one leg of the transfer. In create mode `transfer` is null. In edit mode `transfer` is the leg visible in the current ledger (which may be the "from" leg if `amount < 0`, or the "to" leg if `amount > 0`).

- [ ] **Step 1: Create the component**

Create `frontend/src/app/transactions/transfer-dialog.component.ts`:

```typescript
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Account } from '../core/services/account.service';
import { Transaction } from '../core/services/transaction.service';
import { TransferRequest, TransferService } from '../core/services/transfer.service';

export interface TransferDialogData {
  transfer: Transaction | null;
  currentAccountId: string;
  accounts: Account[];
}

function toLocalDate(dateStr: string): Date {
  return new Date(dateStr + 'T00:00:00');
}

function toDateStr(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function differentAccountsValidator(control: AbstractControl): ValidationErrors | null {
  const from = control.get('fromAccountId')?.value;
  const to = control.get('toAccountId')?.value;
  return from && to && from === to ? { sameAccount: true } : null;
}

@Component({
  selector: 'app-transfer-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButton,
    MatFormField, MatLabel, MatError, MatSuffix,
    MatInput,
    MatSelect, MatOption,
    MatCheckbox,
    MatDatepickerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.transfer ? 'Edit Transfer' : 'New Transfer' }}</h2>
    <mat-dialog-content>
      <form id="transfer-form" [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline">
          <mat-label>From Account</mat-label>
          <mat-select formControlName="fromAccountId">
            @for (a of data.accounts; track a.id) {
              <mat-option [value]="a.id">{{ a.name }}</mat-option>
            }
          </mat-select>
          @if (form.controls.fromAccountId.hasError('required')) {
            <mat-error>From account is required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>To Account</mat-label>
          <mat-select formControlName="toAccountId">
            @for (a of data.accounts; track a.id) {
              <mat-option [value]="a.id">{{ a.name }}</mat-option>
            }
          </mat-select>
          @if (form.controls.toAccountId.hasError('required')) {
            <mat-error>To account is required</mat-error>
          }
          @if (form.hasError('sameAccount')) {
            <mat-error>From and To accounts must be different</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Date</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="date" />
          <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
          @if (form.controls.date.hasError('required')) {
            <mat-error>Date is required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Amount</mat-label>
          <input matInput type="number" formControlName="amount" min="0.01" />
          @if (form.controls.amount.hasError('required')) {
            <mat-error>Amount is required</mat-error>
          }
          @if (form.controls.amount.hasError('min')) {
            <mat-error>Amount must be positive</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Memo</mat-label>
          <input matInput formControlName="memo" />
        </mat-form-field>
        <mat-checkbox formControlName="cleared">Cleared</mat-checkbox>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button form="transfer-form" type="submit" [disabled]="form.invalid">Save</button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content form {
      display: flex;
      flex-direction: column;
      padding-top: 8px;
      min-width: min(400px, 85vw);
      gap: 4px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransferDialogComponent {
  protected readonly data = inject<TransferDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<TransferDialogComponent>);
  private readonly transferService = inject(TransferService);
  private readonly snackBar = inject(MatSnackBar);

  // Determine initial account IDs from the existing leg (edit mode)
  // amount < 0 → current account is "from"; amount > 0 → current account is "to"
  private readonly initFromId = this.data.transfer
    ? (this.data.transfer.amount < 0 ? this.data.transfer.accountId : null)
    : this.data.currentAccountId;
  private readonly initToId = this.data.transfer
    ? (this.data.transfer.amount > 0 ? this.data.transfer.accountId : null)
    : null;

  protected readonly form = new FormGroup({
    fromAccountId: new FormControl<string | null>(this.initFromId, [Validators.required]),
    toAccountId:   new FormControl<string | null>(this.initToId,   [Validators.required]),
    date: new FormControl<Date | null>(
      this.data.transfer ? toLocalDate(this.data.transfer.date) : null,
      [Validators.required]
    ),
    amount: new FormControl<number | null>(
      this.data.transfer ? Math.abs(this.data.transfer.amount) : null,
      [Validators.required, Validators.min(0.01)]
    ),
    memo:    new FormControl(this.data.transfer?.memo ?? '',    { nonNullable: true }),
    cleared: new FormControl(this.data.transfer?.cleared ?? false, { nonNullable: true }),
  }, { validators: differentAccountsValidator });

  protected submit(): void {
    if (this.form.invalid) return;
    const { fromAccountId, toAccountId, date, amount, memo, cleared } = this.form.getRawValue();
    const req: TransferRequest = {
      fromAccountId: fromAccountId!,
      toAccountId:   toAccountId!,
      date: toDateStr(date!),
      amount: Number(amount!),
      memo: memo || null,
      cleared,
    };
    const call$ = this.data.transfer
      ? this.transferService.updateTransfer(this.data.transfer.id, req)
      : this.transferService.createTransfer(req);
    call$.subscribe({
      next: legs => this.dialogRef.close(legs),
      error: () => this.snackBar.open('Failed to save transfer.', 'OK', { duration: 5000 }),
    });
  }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /Users/harrisonbelanger/Desktop/Harrison/Projects/finance-budget-app/frontend
npx ng build --no-progress 2>&1 | tail -20
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/transactions/transfer-dialog.component.ts
git commit -m "feat: add TransferDialogComponent"
```

---

## Task 6: TransactionLedgerComponent — wire up transfers

**Files:**
- Modify: `frontend/src/app/transactions/transaction-ledger.component.ts`
- Modify: `frontend/src/app/transactions/transaction-ledger.component.html`

- [ ] **Step 1: Update the component TypeScript**

Replace the entire contents of `transaction-ledger.component.ts` with:

```typescript
import { AfterViewInit, ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, signal, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import {
  MatCell, MatCellDef, MatColumnDef, MatHeaderCell, MatHeaderCellDef,
  MatHeaderRow, MatHeaderRowDef, MatRow, MatRowDef, MatTable, MatTableDataSource,
} from '@angular/material/table';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { MatPaginator } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { Account, AccountService } from '../core/services/account.service';
import { CategoryGroup, CategoryService } from '../core/services/category.service';
import { Transaction, TransactionService } from '../core/services/transaction.service';
import { TransferService } from '../core/services/transfer.service';
import { BudgetStateService } from '../core/services/budget-state.service';
import { LayoutService } from '../core/services/layout.service';
import { TransactionDialogComponent, TransactionDialogData } from './transaction-dialog.component';
import { TransferDialogComponent, TransferDialogData } from './transfer-dialog.component';
import { ConfirmDialogComponent } from '../accounts/confirm-dialog.component';
import { AppLoadingSpinnerComponent } from '../shared/app-loading-spinner';

@Component({
  selector: 'app-transaction-ledger',
  imports: [
    DatePipe, CurrencyPipe,
    MatTable, MatColumnDef,
    MatHeaderCell, MatHeaderCellDef,
    MatCell, MatCellDef,
    MatHeaderRow, MatHeaderRowDef,
    MatRow, MatRowDef,
    MatSort, MatSortHeader,
    MatPaginator,
    MatButton, MatIconButton,
    MatIcon,
    AppLoadingSpinnerComponent,
  ],
  templateUrl: './transaction-ledger.component.html',
  styleUrl: './transaction-ledger.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class TransactionLedgerComponent implements OnInit, AfterViewInit {
  private readonly route = inject(ActivatedRoute);
  private readonly accountService = inject(AccountService);
  private readonly categoryService = inject(CategoryService);
  private readonly transactionService = inject(TransactionService);
  private readonly transferService = inject(TransferService);
  private readonly budgetState = inject(BudgetStateService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  private readonly accountId = this.route.snapshot.paramMap.get('id')!;
  private readonly month = toSignal(this.budgetState.month$, { requireSync: true });

  protected readonly account = signal<Account | null>(null);
  protected readonly allAccounts = signal<Account[]>([]);
  protected readonly categories = signal<CategoryGroup[]>([]);
  protected readonly categoryMap = computed(() => {
    const map = new Map<string, string>();
    this.categories().forEach(g => g.categories.forEach(c => map.set(c.id, c.name)));
    return map;
  });
  private readonly layout = inject(LayoutService);
  protected readonly isMobile = this.layout.isMobile;

  protected readonly dataSource = new MatTableDataSource<Transaction>();
  protected readonly displayedColumns = computed(() =>
    this.isMobile()
      ? ['mobile']
      : ['date', 'payee', 'category', 'memo', 'amount', 'cleared', 'actions']
  );
  protected isLoading = signal(false);
  protected isWakingUp = signal(false);

  @ViewChild(MatPaginator) private paginator!: MatPaginator;
  @ViewChild(MatSort) private sort!: MatSort;

  private readonly _monthEffect = effect(() => this.loadTransactions(this.month()));

  ngOnInit(): void {
    this.loadAccounts();
    this.loadCategories();
  }

  ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  protected openCreateDialog(): void {
    const data: TransactionDialogData = { transaction: null, accountId: this.accountId, categories: this.categories() };
    this.dialog.open(TransactionDialogComponent, { data })
      .afterClosed()
      .subscribe((t: Transaction | undefined) => {
        if (t) this.dataSource.data = [...this.dataSource.data, t];
      });
  }

  protected openTransferDialog(): void {
    const data: TransferDialogData = { transfer: null, currentAccountId: this.accountId, accounts: this.allAccounts() };
    this.dialog.open(TransferDialogComponent, { data })
      .afterClosed()
      .subscribe((legs: Transaction[] | undefined) => {
        if (!legs) return;
        const myLeg = legs.find(l => l.accountId === this.accountId);
        if (myLeg) this.dataSource.data = [...this.dataSource.data, myLeg];
      });
  }

  protected openEditDialog(transaction: Transaction): void {
    if (transaction.transferId != null) {
      const data: TransferDialogData = { transfer: transaction, currentAccountId: this.accountId, accounts: this.allAccounts() };
      this.dialog.open(TransferDialogComponent, { data })
        .afterClosed()
        .subscribe((legs: Transaction[] | undefined) => {
          if (!legs) return;
          const myLeg = legs.find(l => l.accountId === this.accountId);
          if (myLeg) this.dataSource.data = this.dataSource.data.map(t => t.id === transaction.id ? myLeg : t);
        });
    } else {
      const data: TransactionDialogData = { transaction, accountId: this.accountId, categories: this.categories() };
      this.dialog.open(TransactionDialogComponent, { data })
        .afterClosed()
        .subscribe((updated: Transaction | undefined) => {
          if (updated) this.dataSource.data = this.dataSource.data.map(t => t.id === updated.id ? updated : t);
        });
    }
  }

  protected confirmDelete(transaction: Transaction): void {
    this.dialog.open(ConfirmDialogComponent, { data: { message: 'Delete this transaction? This cannot be undone.' } })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (confirmed) this.deleteTransaction(transaction);
      });
  }

  private loadAccounts(): void {
    this.accountService.getAccounts().subscribe({
      next: accounts => {
        this.allAccounts.set(accounts);
        this.account.set(accounts.find(a => a.id === this.accountId) ?? null);
      },
      error: () => this.snackBar.open('Failed to load account.', 'OK', { duration: 5000 }),
    });
  }

  private loadCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: groups => this.categories.set(groups),
      error: () => this.snackBar.open('Failed to load categories.', 'OK', { duration: 5000 }),
    });
  }

  private loadTransactions(month: string): void {
    this.isLoading.set(true);

    this.transactionService.getTransactions(this.accountId, month).subscribe({
      next: transactions => {
        this.isLoading.set(false);
        this.isWakingUp.set(false);
        this.dataSource.data = transactions;
      },
      error: () => {
        if (this.isWakingUp()) {
          this.loadTransactions(month);
        } else {
          this.isLoading.set(false);
          this.isWakingUp.set(false);
          this.snackBar.open('Failed to load transactions.', 'OK', { duration: 5000 });
        }
      },
    });

    timer(5000).subscribe(() => {
      if (this.isLoading()) this.isWakingUp.set(true);
    });
  }

  private deleteTransaction(transaction: Transaction): void {
    const delete$ = transaction.transferId != null
      ? this.transferService.deleteTransfer(transaction.id)
      : this.transactionService.deleteTransaction(transaction.id);

    delete$.subscribe({
      next: () => {
        this.dataSource.data = this.dataSource.data.filter(t =>
          t.id !== transaction.id && t.id !== transaction.transferId
        );
      },
      error: () => this.snackBar.open('Failed to delete transaction.', 'OK', { duration: 5000 }),
    });
  }
}
```

- [ ] **Step 2: Update the HTML template — add Transfer button and fix category column**

Replace `transaction-ledger.component.html` with the following (the only changes are: add "Transfer" button in the header, and update the category column to show "Transfer" when `transferId != null`):

```html
@if (isLoading()) {
  <app-loading-spinner [isLoading]="isLoading()" [isWakingUp]="isWakingUp()" loadingText="Loading transactions..."></app-loading-spinner>
} @else {
<div class="page-header">
  <div class="account-info">
    <h1 class="account-title">{{ account()?.name ?? '' }}</h1>
    <span class="account-balance"
      [class.positive]="(account()?.balance ?? 0) > 0"
      [class.negative]="(account()?.balance ?? 0) < 0">
      {{ account()?.balance | currency }}
    </span>
  </div>
  <div class="header-actions">
    <button mat-button (click)="openTransferDialog()">Add Transfer</button>
    <button mat-flat-button (click)="openCreateDialog()">Add Transaction</button>
  </div>
</div>

<mat-table [dataSource]="dataSource" matSort matSortActive="date" matSortDirection="desc">

  <ng-container matColumnDef="mobile">
    <mat-header-cell *matHeaderCellDef></mat-header-cell>
    <mat-cell *matCellDef="let t">
      <div class="mobile-card">
        <div class="mobile-row-primary">
          <span class="mobile-payee">{{ t.payee ?? '—' }}</span>
          <span [class.positive]="t.amount > 0" [class.negative]="t.amount < 0" class="mobile-amount">
            {{ t.amount | currency }}
          </span>
        </div>
        <div class="mobile-row-secondary">
          <div class="mobile-meta-group">
            <span class="mobile-date">{{ t.date | date:'mediumDate':'UTC' }}</span>
            @if (t.transferId) {
              <span class="mobile-meta"> · Transfer</span>
            } @else if (t.categoryId) {
              <span class="mobile-meta"> · {{ categoryMap().get(t.categoryId) }}</span>
            }
            @if (t.memo) {
              <span class="mobile-meta"> · {{ t.memo }}</span>
            }
            <mat-icon [class.cleared]="t.cleared" [class.uncleared]="!t.cleared" class="mobile-cleared">
              {{ t.cleared ? 'check_circle' : 'radio_button_unchecked' }}
            </mat-icon>
          </div>
          <div class="mobile-actions">
            <button mat-icon-button (click)="openEditDialog(t)" aria-label="Edit transaction">
              <mat-icon>edit</mat-icon>
            </button>
            <button mat-icon-button (click)="confirmDelete(t)" aria-label="Delete transaction">
              <mat-icon>delete</mat-icon>
            </button>
          </div>
        </div>
      </div>
    </mat-cell>
  </ng-container>

  <ng-container matColumnDef="date">
    <mat-header-cell *matHeaderCellDef mat-sort-header>Date</mat-header-cell>
    <mat-cell *matCellDef="let t">{{ t.date | date:'mediumDate':'UTC' }}</mat-cell>
  </ng-container>

  <ng-container matColumnDef="payee">
    <mat-header-cell *matHeaderCellDef mat-sort-header>Payee</mat-header-cell>
    <mat-cell *matCellDef="let t">{{ t.payee ?? '—' }}</mat-cell>
  </ng-container>

  <ng-container matColumnDef="category">
    <mat-header-cell *matHeaderCellDef>Category</mat-header-cell>
    <mat-cell *matCellDef="let t">
      @if (t.transferId) {
        Transfer
      } @else {
        {{ t.categoryId ? (categoryMap().get(t.categoryId) ?? '—') : '—' }}
      }
    </mat-cell>
  </ng-container>

  <ng-container matColumnDef="memo">
    <mat-header-cell *matHeaderCellDef>Memo</mat-header-cell>
    <mat-cell *matCellDef="let t">{{ t.memo ?? '' }}</mat-cell>
  </ng-container>

  <ng-container matColumnDef="amount">
    <mat-header-cell *matHeaderCellDef mat-sort-header>Amount</mat-header-cell>
    <mat-cell *matCellDef="let t">
      <span
        [class.positive]="t.amount > 0"
        [class.negative]="t.amount < 0"
      >{{ t.amount | currency }}</span>
    </mat-cell>
  </ng-container>

  <ng-container matColumnDef="cleared">
    <mat-header-cell *matHeaderCellDef>Cleared</mat-header-cell>
    <mat-cell *matCellDef="let t">
      <mat-icon [class.cleared]="t.cleared" [class.uncleared]="!t.cleared">
        {{ t.cleared ? 'check_circle' : 'radio_button_unchecked' }}
      </mat-icon>
    </mat-cell>
  </ng-container>

  <ng-container matColumnDef="actions">
    <mat-header-cell *matHeaderCellDef></mat-header-cell>
    <mat-cell *matCellDef="let t">
      <button mat-icon-button (click)="openEditDialog(t)" aria-label="Edit transaction">
        <mat-icon>edit</mat-icon>
      </button>
      <button mat-icon-button (click)="confirmDelete(t)" aria-label="Delete transaction">
        <mat-icon>delete</mat-icon>
      </button>
    </mat-cell>
  </ng-container>

  <mat-header-row *matHeaderRowDef="displayedColumns()"></mat-header-row>
  <mat-row *matRowDef="let row; columns: displayedColumns();"></mat-row>

</mat-table>

<mat-paginator [pageSizeOptions]="[25, 50, 100]" showFirstLastButtons></mat-paginator>
}
```

- [ ] **Step 3: Build to check for TypeScript/template errors**

```bash
cd /Users/harrisonbelanger/Desktop/Harrison/Projects/finance-budget-app/frontend
npx ng build --no-progress 2>&1 | tail -30
```

Expected: no errors. The `.header-actions` class in the HTML should just work since the SCSS already has flex rules for `.page-header`. If the buttons don't align, add to `transaction-ledger.component.scss`:

```scss
.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
```

- [ ] **Step 4: Start the app and manually test the golden path**

```bash
# In terminal 1 — backend
cd /Users/harrisonbelanger/Desktop/Harrison/Projects/finance-budget-app
./mvnw spring-boot:run

# In terminal 2 — frontend
cd /Users/harrisonbelanger/Desktop/Harrison/Projects/finance-budget-app/frontend
npx ng serve
```

Open `http://localhost:4200`, go to any account's ledger, and verify:
- "Add Transfer" button is visible next to "Add Transaction"
- Clicking "Add Transfer" opens the TransferDialogComponent with From/To account selects
- Creating a transfer creates two linked rows (the current account's leg appears in the table)
- Transfer rows show "Transfer" in the Category column
- Deleting a transfer row shows the confirm dialog then removes it
- Editing a transfer row opens the TransferDialogComponent with date/amount/memo pre-filled

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/transactions/transaction-ledger.component.ts
git add frontend/src/app/transactions/transaction-ledger.component.html
git add frontend/src/app/transactions/transaction-ledger.component.scss  # if modified
git commit -m "feat: wire up transfer dialog and delete/edit routing in transaction ledger"
```

---

## Spec Coverage Check

| Spec requirement | Task |
|------------------|------|
| `createTransfer` saves two linked rows | Task 1 |
| `deleteTransfer` removes both legs | Task 1 |
| `updateTransfer` syncs both legs | Task 1 |
| Auth: wrong `userSub` throws 403 | Task 1 |
| `TransactionService.delete()` on transfer leg returns 409 | Task 2 |
| `POST /api/transfers` → 201 | Task 3 |
| `PUT /api/transfers/{id}` → 200 | Task 3 |
| `DELETE /api/transfers/{id}` → 204 | Task 3 |
| `DELETE /api/transactions/{id}` on transfer leg → 409 | Task 2 |
| `Transaction` interface gets `transferId` | Task 4 |
| `TransferService` frontend with 3 methods | Task 4 |
| `TransferDialogComponent` with accounts + cross-account validator | Task 5 |
| Ledger: "Transfer" button → opens dialog | Task 6 |
| Ledger: after create, show leg in table | Task 6 |
| Ledger: delete transfer row calls `transferService.deleteTransfer` | Task 6 |
| Ledger: edit transfer row opens `TransferDialogComponent` | Task 6 |
| Ledger: Category column shows "Transfer" for transfer rows | Task 6 |
