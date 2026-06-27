# FBA-39 — Credit Card Budget Mechanics: Design Spec

**Date:** 2026-05-21  
**Ticket:** FBA-39  
**Status:** Approved for implementation

---

## Overview

Implement YNAB-style credit card budget mechanics. When a user spends on a credit card, the money moves automatically from the spending category envelope to a CC Payment envelope — readyToAssign is unaffected. When the bill is paid (transfer to CC account), the CC Payment balance decreases.

---

## Core Mechanics

### readyToAssign

CC purchases are a liability shift, not a cash outflow. They must be excluded from the net income calculation:

```
readyToAssign = sumNetExcludingCCPurchases − totalAssigned
```

A **CC purchase** is defined as: a transaction where `account.type = CREDIT_CARD`, `categoryId IS NOT NULL`, and `transferId IS NULL`.

`totalAssigned` requires no change — CC Payment has no entries in `budget_allocations`, so it is excluded automatically.

### CC Payment available

Computed dynamically in `getBudget()` from the CC account's live transaction balance:

```
CC Payment available = max(0, −SUM(all transactions on that CC account through end of month))
```

This is inherently cumulative — unpaid balances carry forward across months with no extra logic.

### Envelope movement

When a user spends $120 on groceries via CC:
- **Groceries**: `assigned=$200, spent=−$120, available=$80` — the purchase shows in `spent` automatically
- **CC Payment**: `available=$120` — derived from CC account balance
- **readyToAssign**: unchanged

When the user pays $100 toward the CC bill (transfer from Checking → CC):
- **CC Payment**: `available=$20` — CC balance improved by $100
- **readyToAssign**: unchanged — the transfer is zero-sum in `sumNetExcludingCCPurchases`

---

## Data Model

### Migration V11

```sql
ALTER TABLE accounts
  ADD COLUMN cc_payment_category_id UUID REFERENCES budget_categories(id);
```

Backfill for existing CC accounts:
1. For each user with one or more `CREDIT_CARD` accounts: insert one `"Credit Card Payments"` category group owned by that user, or reuse the existing group with that exact name if one already exists for the user
2. For each CC account: insert one `budget_categories` row named after the account, placed in that group
3. Update `accounts.cc_payment_category_id` to point to the new category

Users with no CC accounts are unaffected.

### Account entity

```java
@Column(name = "cc_payment_category_id")
private UUID ccPaymentCategoryId;  // null for non-CC accounts
```

### BudgetCategoryViewDTO

Add `boolean systemManaged` field. Set to `true` for CC Payment categories. Used by the frontend to render the row differently.

---

## Backend

### `CreditCardService` (new)

Owns all CC category lifecycle logic. Three methods:

```java
// Creates "Credit Card Payments" group (or reuses existing for this user),
// creates a category named after the account, sets account.ccPaymentCategoryId, saves account.
void ensureCCPaymentCategory(Account account, String userSub)

// Renames the linked payment category to match the account's current name.
void syncCategoryName(Account account)

// Clears account.ccPaymentCategoryId, deletes the linked category,
// deletes the "Credit Card Payments" group if no other CC Payment categories remain in it.
void deleteCCPaymentCategory(Account account)
```

### `AccountService`

- `create()`: after save, if `type == CREDIT_CARD` → `ensureCCPaymentCategory`
- `update()`:
  - name changed and type is (or remains) `CREDIT_CARD` → `syncCategoryName` after save
  - type changed to `CREDIT_CARD` → `ensureCCPaymentCategory` after save
  - type changed away from `CREDIT_CARD` → `deleteCCPaymentCategory` before save

Account type changes are supported today (`AccountService.update()` calls `account.setType()`), so all three type-change cases are handled.

### `BudgetService`

**readyToAssign fix:**

Replace `sumNetUpToDate` with a new repository query:

```java
BigDecimal totalNet = transactionRepository.sumNetExcludingCCPurchases(lastDay, userSub);
```

New JPQL query:
```java
@Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
       "WHERE t.date <= :lastDay AND t.account.userSub = :userSub " +
       "AND NOT (t.account.type = 'CREDIT_CARD' AND t.categoryId IS NOT NULL AND t.transferId IS NULL)")
BigDecimal sumNetExcludingCCPurchases(@Param("lastDay") LocalDate lastDay, @Param("userSub") String userSub);
```

**CC Payment display in `getBudget()`:**

Before assembling groups, load a map of CC Payment category IDs to account IDs:
```java
Map<UUID, UUID> ccPaymentCategoryToAccount = accountRepository
    .findByUserSubAndCCPaymentCategoryIdNotNull(userSub)
    .stream()
    .collect(Collectors.toMap(Account::getCcPaymentCategoryId, Account::getId));
```

When building each `BudgetCategoryViewDTO`, check if the category is in this map. If yes:
```java
BigDecimal balance = transactionRepository.sumForAccount(accountId, lastDay);
BigDecimal owed = balance.negate().max(BigDecimal.ZERO);
return new BudgetCategoryViewDTO(c.getId(), c.getName(), owed, BigDecimal.ZERO, owed, true);
// systemManaged=true signals the frontend to render the "owed" display
```

New repository query:
```java
@Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.account.id = :accountId AND t.date <= :lastDay")
BigDecimal sumForAccount(@Param("accountId") UUID accountId, @Param("lastDay") LocalDate lastDay);
```

If no: use the existing `assigned + spent` formula with `systemManaged=false`.

### `CategoryService`

Block rename and delete for system-managed categories:

```java
// Add to deleteCategory(), renameCategory():
if (accountRepository.existsByCCPaymentCategoryId(id)) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "This category is managed by a linked credit card account");
}

// Add to deleteGroup():
if (budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)
        .stream().anyMatch(c -> accountRepository.existsByCCPaymentCategoryId(c.getId()))) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "This group contains a category managed by a linked credit card account");
}
```

### `TransactionService` / `TransferService`

No changes required.

---

## Frontend

### `BudgetService` (TypeScript)

Add `systemManaged` to the category interface:

```typescript
interface BudgetCategory {
  id: string;
  name: string;
  assigned: number;
  spent: number;
  available: number;
  systemManaged: boolean;
}
```

### Budget component

For `systemManaged` category rows:
- Hide the assigned input field
- Hide the spent column
- Hide the rename and delete icon buttons
- Show a single read-only **"Owed"** value in place of the three-column layout

For all other rows: no change.

---

## Acceptance Criteria

- Creating a CC account auto-creates a linked "Credit Card Payments" category
- Existing CC accounts have linked payment categories created via migration
- The CC Payment category row in the budget view shows a single read-only "Owed" value
- Recording a CC purchase does not change readyToAssign; the spending category available decreases and CC Payment owed increases
- Editing or deleting a CC purchase correctly adjusts CC Payment owed (derived from live account balance — no extra logic needed)
- Paying the CC bill via transfer reduces CC Payment owed proportionally
- Unpaid balances carry forward across months automatically
- Renaming a CC account renames its payment category
- Changing account type to CREDIT_CARD creates a payment category; changing away deletes it
- Deleting or renaming a CC Payment category from the budget UI returns a 409 error
- All behavior persists after page refresh
