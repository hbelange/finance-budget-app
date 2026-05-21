# FBA-27: Transfers Feature — Progress Writeup

## Scope

Transfers between accounts, with exclusion from income/spending reports.
Credit card budget mechanics (auto-moving budget to CC Payment category) deferred to a follow-up ticket.

## Architecture Decisions

- **Schema**: Self-referential nullable FK `transfer_id` on `transactions` table. Each leg stores the other leg's ID. `ON DELETE SET NULL` as a safety net.
- **Backend**: Separate `TransferService` + `TransferController` at `/api/transfers` (not bolted onto `TransactionService`). Keeps single-transaction CRUD and paired-transaction orchestration as distinct responsibilities.
- **Transfer exclusion**: `AND t.transferId IS NULL` added to `sumIncomeForMonth` and `sumSpentForMonth`. The budget category queries (`findSpentByCategoryForMonth`, `findExpenseByCategoryForMonth`) already have `categoryId IS NOT NULL` — transfers always have null categoryId, so they're already excluded.
- **Delete**: Both legs deleted atomically. `TransactionService.delete()` guards with 409 CONFLICT if someone tries to delete a transfer leg via the regular endpoint.
- **Edit**: Both legs synced atomically. Determines which is the outflow leg by checking `amount < 0`.

---

## Completed

### Backend

| File | Change |
|------|--------|
| `db/migration/V10__add_transfers.sql` | Adds `transfer_id UUID REFERENCES transactions(id) ON DELETE SET NULL` + index |
| `entity/Transaction.java` | Added `private UUID transferId` field |
| `dto/TransactionDTO.java` | Added `UUID transferId` as last field |
| `service/TransactionService.java` | Updated `toDTO()` to include `transferId`; added 409 CONFLICT guard in `delete()` |
| `repository/TransactionRepository.java` | Added `AND t.transferId IS NULL` to `sumIncomeForMonth` and `sumSpentForMonth` |
| `dto/TransferRequest.java` | New record: `fromAccountId`, `toAccountId`, `date`, `amount`, `memo`, `cleared` |
| `service/TransferService.java` | `createTransfer`, `updateTransfer`, `deleteTransfer` — all `@Transactional` |
| `controller/TransferController.java` | POST `/api/transfers` (201), PUT `/api/transfers/{id}` (200), DELETE `/api/transfers/{id}` (204) |

Build passes: `./mvnw clean package -DskipTests` ✓

---

## Still To Do

### Backend — Tests

- [ ] `TransferServiceTest` — write unit/integration tests:
  - `createTransfer` saves two linked rows with cross-referencing `transferId`s
  - `deleteTransfer` removes both legs atomically
  - `updateTransfer` syncs both legs (date, amount, accounts, memo)
  - Auth: wrong `userSub` throws 403
  - `TransactionService.delete()` on a transfer leg returns 409
- [ ] `TransferControllerTest` — integration tests:
  - `POST /api/transfers` → 201, returns both legs
  - `PUT /api/transfers/{id}` → 200, returns updated legs
  - `DELETE /api/transfers/{id}` → 204
  - `DELETE /api/transactions/{id}` on a transfer leg → 409

### Frontend

- [ ] **`transaction.service.ts`** — add `transferId: string | null` to the `Transaction` interface
- [ ] **`transfer.service.ts`** — new service with three methods:
  - `createTransfer(req): Observable<Transaction[]>`
  - `updateTransfer(id, req): Observable<Transaction[]>`
  - `deleteTransfer(id): Observable<void>`
- [ ] **`transfer-dialog.component.ts`** — new standalone OnPush dialog:
  - Form fields: `fromAccountId` (MatSelect, pre-filled with current account), `toAccountId` (MatSelect, filtered to exclude fromAccount), `date` (MatDatepicker), `amount` (positive number), `memo`, `cleared`
  - Cross-account validator: prevents selecting the same account for both sides
  - Create mode: calls `TransferService.createTransfer`
  - Edit mode: calls `TransferService.updateTransfer`
  - Closes with `Transaction[]` so the ledger can update without a reload
- [ ] **`transaction-ledger.component.ts`** — wire up:
  - Store all accounts in an `accounts` signal (already loads them in `loadAccount()`, just needs to expose the full list)
  - Add "Transfer" button next to "Add Transaction" → opens `TransferDialogComponent`
  - `afterClosed()` on create: add the leg matching `this.accountId` to `dataSource.data`
  - On delete: if `t.transferId != null`, call `TransferService.deleteTransfer(t.id)` instead of `TransactionService.deleteTransaction`; remove both legs from `dataSource.data`
  - On edit: if `t.transferId != null`, open `TransferDialogComponent` instead of `TransactionDialogComponent`
  - Category column: display `"Transfer"` when `t.transferId != null`

---

## Key Concepts Covered

- **Self-referential FK**: a table's FK pointing back to itself. Used here so each transfer leg stores its partner's ID.
- **ON DELETE SET NULL vs CASCADE**: SET NULL orphans safely; CASCADE silently removes data. For a finance app, silent data loss is worse than an orphaned row.
- **@Transactional atomicity**: all saves/deletes within the method commit or roll back together. Required for the 3-save create sequence and the null-then-delete sequence.
- **JPA INSERT vs UPDATE**: saving a new entity (no ID) → INSERT. Saving a loaded entity (has ID) → UPDATE. `updateTransfer` must mutate the loaded entities, not create new ones.
- **Repository ownership**: multiple services can inject the same repository. `TransferService` uses `TransactionRepository` — that's fine. Service boundaries are about business logic, not data access.
- **409 CONFLICT vs 400 BAD REQUEST**: 400 = malformed request. 409 = valid request that conflicts with current resource state.
