# FBA-39 Credit Card Budget Mechanics — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement YNAB-style CC budget mechanics so CC purchases do not affect readyToAssign, the CC Payment row shows a live "Owed" value, and system-managed CC Payment categories cannot be renamed or deleted by users.

**Architecture:** `CreditCardService` (new) owns CC category lifecycle — creates, renames, and deletes the linked "CC Payment" category when a CC account is created, renamed, or type-changed. `BudgetService.getBudget()` replaces `sumNetUpToDate` with a query that excludes CC purchases and adds dynamic CC Payment computation from the live account balance. `CategoryService` injects `AccountRepository` to block modification of system-managed categories. Frontend adds `systemManaged` to `BudgetCategory` and renders a read-only "Owed" row instead of the three-column layout.

**Tech Stack:** Java 21 / Spring Boot 3 / Flyway / JPA / PostgreSQL / H2 (tests) / Mockito (@ExtendWith(MockitoExtension.class)) / @DataJpaTest (repository integration tests) / Angular 21 (standalone components, signals)

---

## File Map

| File | Action |
|------|--------|
| `backend/src/main/resources/db/migration/V11__add_cc_payment_category.sql` | Create |
| `backend/src/main/java/.../entity/Account.java` | Modify — add `ccPaymentCategoryId` field |
| `backend/src/main/java/.../repository/AccountRepository.java` | Modify — add 2 queries |
| `backend/src/main/java/.../repository/CategoryGroupRepository.java` | Modify — add `findByUserSubAndName` |
| `backend/src/main/java/.../repository/TransactionRepository.java` | Modify — add 2 queries |
| `backend/src/main/java/.../service/CreditCardService.java` | Create |
| `backend/src/main/java/.../service/AccountService.java` | Modify — inject CreditCardService, add CC hooks in create/update |
| `backend/src/main/java/.../service/CategoryService.java` | Modify — inject AccountRepository, add systemManaged guards |
| `backend/src/main/java/.../dto/BudgetCategoryViewDTO.java` | Modify — add `systemManaged` field |
| `backend/src/main/java/.../service/BudgetService.java` | Modify — use new query + CC Payment display, inject AccountRepository |
| `backend/src/test/java/.../service/CreditCardServiceTest.java` | Create |
| `backend/src/test/java/.../service/AccountServiceTest.java` | Modify — mock CreditCardService, add CC hook tests |
| `backend/src/test/java/.../service/CategoryServiceTest.java` | Modify — add systemManaged guard tests |
| `backend/src/test/java/.../service/BudgetServiceTest.java` | Modify — update to new query, add CC Payment display tests |
| `backend/src/test/java/.../repository/TransactionRepositoryTest.java` | Modify — add new query tests |
| `frontend/src/app/core/services/budget.service.ts` | Modify — add `systemManaged` to interface |
| `frontend/src/app/budget/budget.component.html` | Modify — render systemManaged rows as read-only "Owed" |

---

## Task 1: V11 Flyway Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__add_cc_payment_category.sql`

- [ ] **Step 1: Write the migration**

```sql
ALTER TABLE accounts
  ADD COLUMN cc_payment_category_id UUID REFERENCES budget_categories(id);

-- Create "Credit Card Payments" group for each user that has CC accounts (if not already present)
INSERT INTO category_groups (id, name, sort_order, user_sub)
SELECT gen_random_uuid(),
       'Credit Card Payments',
       COALESCE((SELECT MAX(cg2.sort_order) FROM category_groups cg2 WHERE cg2.user_sub = ua.user_sub), -1) + 1,
       ua.user_sub
FROM (SELECT DISTINCT user_sub FROM accounts WHERE type = 'CREDIT_CARD') ua
WHERE NOT EXISTS (
  SELECT 1 FROM category_groups cg
  WHERE cg.user_sub = ua.user_sub AND cg.name = 'Credit Card Payments'
);

-- Create one budget_category per CC account, named after the account
INSERT INTO budget_categories (id, group_id, name, sort_order)
SELECT gen_random_uuid(), cg.id, a.name, 0
FROM accounts a
JOIN category_groups cg ON cg.user_sub = a.user_sub AND cg.name = 'Credit Card Payments'
WHERE a.type = 'CREDIT_CARD';

-- Point each CC account at its new payment category
UPDATE accounts
SET cc_payment_category_id = (
  SELECT bc.id
  FROM budget_categories bc
  JOIN category_groups cg ON bc.group_id = cg.id
  WHERE cg.user_sub = accounts.user_sub
    AND cg.name = 'Credit Card Payments'
    AND bc.name = accounts.name
)
WHERE accounts.type = 'CREDIT_CARD';
```

- [ ] **Step 2: Verify migration compiles**

Run: `./mvnw clean package -DskipTests`

Expected: BUILD SUCCESS — Flyway runs V11 against H2 on startup.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V11__add_cc_payment_category.sql
git commit -m "FBA-39: V11 migration — add cc_payment_category_id + backfill"
```

---

## Task 2: Account Entity + Repository Queries

**Files:**
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/entity/Account.java`
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/repository/AccountRepository.java`
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/repository/CategoryGroupRepository.java`
- Test: `backend/src/test/java/com/hbelange/financebudgetapp/repository/AccountRepositoryTest.java`

- [ ] **Step 1: Write failing tests for new repository methods**

Add to `AccountRepositoryTest.java` (after existing tests):

```java
// --- findByUserSubAndCCPaymentCategoryIdNotNull ---

@Test
void findByUserSubAndCCPaymentCategoryIdNotNull_returnsOnlyLinkedAccounts() {
    // arrange
    CategoryGroup group = new CategoryGroup();
    group.setName("Credit Card Payments");
    group.setSortOrder(99);
    group.setUserSub("auth0|test-user");
    group = categoryGroupRepository.save(group);

    BudgetCategory cat = new BudgetCategory();
    cat.setGroup(group);
    cat.setName("My Visa");
    cat.setSortOrder(0);
    cat = budgetCategoryRepository.save(cat);

    Account cc = new Account();
    cc.setName("My Visa");
    cc.setType(AccountType.CREDIT_CARD);
    cc.setUserSub("auth0|test-user");
    cc.setCcPaymentCategoryId(cat.getId());
    cc = accountRepository.save(cc);

    List<Account> result = accountRepository.findByUserSubAndCCPaymentCategoryIdNotNull("auth0|test-user");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(cc.getId());
}

@Test
void findByUserSubAndCCPaymentCategoryIdNotNull_excludesNonLinkedAccounts() {
    List<Account> result = accountRepository.findByUserSubAndCCPaymentCategoryIdNotNull("auth0|test-user");
    assertThat(result).isEmpty();
}

// --- existsByCCPaymentCategoryId ---

@Test
void existsByCCPaymentCategoryId_returnsTrueWhenLinked() {
    CategoryGroup group = new CategoryGroup();
    group.setName("Credit Card Payments");
    group.setSortOrder(99);
    group.setUserSub("auth0|test-user");
    group = categoryGroupRepository.save(group);

    BudgetCategory cat = new BudgetCategory();
    cat.setGroup(group);
    cat.setName("My Visa");
    cat.setSortOrder(0);
    cat = budgetCategoryRepository.save(cat);

    Account cc = new Account();
    cc.setName("My Visa");
    cc.setType(AccountType.CREDIT_CARD);
    cc.setUserSub("auth0|test-user");
    cc.setCcPaymentCategoryId(cat.getId());
    accountRepository.save(cc);

    assertTrue(accountRepository.existsByCCPaymentCategoryId(cat.getId()));
}

@Test
void existsByCCPaymentCategoryId_returnsFalseWhenNotLinked() {
    assertFalse(accountRepository.existsByCCPaymentCategoryId(UUID.randomUUID()));
}
```

The test class will also need these imports and autowired repos:
```java
@Autowired
private CategoryGroupRepository categoryGroupRepository;

@Autowired
private BudgetCategoryRepository budgetCategoryRepository;
```

And at the top of the file, add imports:
```java
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `./mvnw test -Dtest=AccountRepositoryTest -pl backend`

Expected: FAIL — `findByUserSubAndCCPaymentCategoryIdNotNull` and `existsByCCPaymentCategoryId` do not exist yet.

- [ ] **Step 3: Add `ccPaymentCategoryId` to Account entity**

In `Account.java`, add after the `userSub` field:

```java
@Column(name = "cc_payment_category_id")
private UUID ccPaymentCategoryId;
```

- [ ] **Step 4: Add new methods to AccountRepository**

```java
List<Account> findByUserSubAndCCPaymentCategoryIdNotNull(String userSub);

boolean existsByCCPaymentCategoryId(UUID ccPaymentCategoryId);
```

- [ ] **Step 5: Add `findByUserSubAndName` to CategoryGroupRepository**

```java
Optional<CategoryGroup> findByUserSubAndName(String userSub, String name);
```

- [ ] **Step 6: Run tests to confirm they pass**

Run: `./mvnw test -Dtest=AccountRepositoryTest -pl backend`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/hbelange/financebudgetapp/entity/Account.java \
        backend/src/main/java/com/hbelange/financebudgetapp/repository/AccountRepository.java \
        backend/src/main/java/com/hbelange/financebudgetapp/repository/CategoryGroupRepository.java \
        backend/src/test/java/com/hbelange/financebudgetapp/repository/AccountRepositoryTest.java
git commit -m "FBA-39: Account entity cc_payment_category_id field + repository queries"
```

---

## Task 3: CreditCardService

**Files:**
- Create: `backend/src/main/java/com/hbelange/financebudgetapp/service/CreditCardService.java`
- Create: `backend/src/test/java/com/hbelange/financebudgetapp/service/CreditCardServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `CreditCardServiceTest.java`:

```java
package com.hbelange.financebudgetapp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.enums.AccountType;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @Mock private CategoryGroupRepository categoryGroupRepository;
    @Mock private BudgetCategoryRepository budgetCategoryRepository;
    @Mock private AccountRepository accountRepository;

    @InjectMocks
    private CreditCardService creditCardService;

    private static final String USER_SUB = "auth0|test-user";

    private Account ccAccount;
    private CategoryGroup ccGroup;
    private BudgetCategory ccCategory;

    @BeforeEach
    void setUp() {
        ccAccount = new Account();
        ccAccount.setId(UUID.randomUUID());
        ccAccount.setName("My Visa");
        ccAccount.setType(AccountType.CREDIT_CARD);
        ccAccount.setUserSub(USER_SUB);

        ccGroup = new CategoryGroup();
        ccGroup.setId(UUID.randomUUID());
        ccGroup.setName("Credit Card Payments");
        ccGroup.setSortOrder(5);
        ccGroup.setUserSub(USER_SUB);

        ccCategory = new BudgetCategory();
        ccCategory.setId(UUID.randomUUID());
        ccCategory.setGroup(ccGroup);
        ccCategory.setName("My Visa");
        ccCategory.setSortOrder(0);
    }

    // --- ensureCCPaymentCategory ---

    @Test
    void ensureCCPaymentCategory_createsGroupAndCategory_whenGroupDoesNotExist() {
        when(categoryGroupRepository.findByUserSubAndName(USER_SUB, "Credit Card Payments"))
            .thenReturn(Optional.empty());
        when(categoryGroupRepository.findTopByUserSubOrderBySortOrderDesc(USER_SUB))
            .thenReturn(Optional.of(ccGroup));
        when(categoryGroupRepository.save(any(CategoryGroup.class))).thenReturn(ccGroup);
        when(budgetCategoryRepository.save(any(BudgetCategory.class))).thenReturn(ccCategory);
        when(accountRepository.save(any(Account.class))).thenReturn(ccAccount);

        creditCardService.ensureCCPaymentCategory(ccAccount, USER_SUB);

        verify(categoryGroupRepository).save(argThat(g ->
            g.getName().equals("Credit Card Payments") && g.getUserSub().equals(USER_SUB)));
        verify(budgetCategoryRepository).save(argThat(c -> c.getName().equals("My Visa")));
        verify(accountRepository).save(argThat(a -> a.getCcPaymentCategoryId() != null));
    }

    @Test
    void ensureCCPaymentCategory_reusesExistingGroup() {
        when(categoryGroupRepository.findByUserSubAndName(USER_SUB, "Credit Card Payments"))
            .thenReturn(Optional.of(ccGroup));
        when(budgetCategoryRepository.save(any(BudgetCategory.class))).thenReturn(ccCategory);
        when(accountRepository.save(any(Account.class))).thenReturn(ccAccount);

        creditCardService.ensureCCPaymentCategory(ccAccount, USER_SUB);

        verify(categoryGroupRepository, never()).save(any(CategoryGroup.class));
        verify(budgetCategoryRepository).save(argThat(c -> c.getName().equals("My Visa")));
    }

    // --- syncCategoryName ---

    @Test
    void syncCategoryName_renamesLinkedCategory() {
        ccAccount.setCcPaymentCategoryId(ccCategory.getId());
        when(budgetCategoryRepository.findById(ccCategory.getId())).thenReturn(Optional.of(ccCategory));

        creditCardService.syncCategoryName(ccAccount);

        verify(budgetCategoryRepository).save(argThat(c -> c.getName().equals("My Visa")));
    }

    // --- deleteCCPaymentCategory ---

    @Test
    void deleteCCPaymentCategory_deletesCategoryAndClearsField() {
        ccAccount.setCcPaymentCategoryId(ccCategory.getId());
        when(budgetCategoryRepository.findById(ccCategory.getId())).thenReturn(Optional.of(ccCategory));
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(ccGroup)).thenReturn(List.of());

        creditCardService.deleteCCPaymentCategory(ccAccount);

        assertNull(ccAccount.getCcPaymentCategoryId());
        verify(budgetCategoryRepository).deleteById(ccCategory.getId());
    }

    @Test
    void deleteCCPaymentCategory_deletesGroupWhenNoOtherCCCategories() {
        ccAccount.setCcPaymentCategoryId(ccCategory.getId());
        when(budgetCategoryRepository.findById(ccCategory.getId())).thenReturn(Optional.of(ccCategory));
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(ccGroup)).thenReturn(List.of());

        creditCardService.deleteCCPaymentCategory(ccAccount);

        verify(categoryGroupRepository).deleteById(ccGroup.getId());
    }

    @Test
    void deleteCCPaymentCategory_keepsGroupWhenOtherCCCategoriesRemain() {
        ccAccount.setCcPaymentCategoryId(ccCategory.getId());
        BudgetCategory other = new BudgetCategory();
        other.setId(UUID.randomUUID());
        other.setGroup(ccGroup);
        other.setName("Other Card");
        other.setSortOrder(1);

        when(budgetCategoryRepository.findById(ccCategory.getId())).thenReturn(Optional.of(ccCategory));
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(ccGroup)).thenReturn(List.of(other));

        creditCardService.deleteCCPaymentCategory(ccAccount);

        verify(categoryGroupRepository, never()).deleteById(any());
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `./mvnw test -Dtest=CreditCardServiceTest -pl backend`

Expected: FAIL — `CreditCardService` does not exist.

- [ ] **Step 3: Implement CreditCardService**

Create `CreditCardService.java`:

```java
package com.hbelange.financebudgetapp.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;

@Service
public class CreditCardService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final AccountRepository accountRepository;

    @Autowired
    public CreditCardService(CategoryGroupRepository categoryGroupRepository,
                              BudgetCategoryRepository budgetCategoryRepository,
                              AccountRepository accountRepository) {
        this.categoryGroupRepository = categoryGroupRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void ensureCCPaymentCategory(Account account, String userSub) {
        CategoryGroup group = categoryGroupRepository
            .findByUserSubAndName(userSub, "Credit Card Payments")
            .orElseGet(() -> {
                int nextOrder = categoryGroupRepository
                    .findTopByUserSubOrderBySortOrderDesc(userSub)
                    .map(g -> g.getSortOrder() + 1)
                    .orElse(0);
                CategoryGroup newGroup = new CategoryGroup();
                newGroup.setName("Credit Card Payments");
                newGroup.setSortOrder(nextOrder);
                newGroup.setUserSub(userSub);
                return categoryGroupRepository.save(newGroup);
            });

        BudgetCategory category = new BudgetCategory();
        category.setGroup(group);
        category.setName(account.getName());
        category.setSortOrder(0);
        BudgetCategory saved = budgetCategoryRepository.save(category);

        account.setCcPaymentCategoryId(saved.getId());
        accountRepository.save(account);
    }

    @Transactional
    public void syncCategoryName(Account account) {
        budgetCategoryRepository.findById(account.getCcPaymentCategoryId()).ifPresent(cat -> {
            cat.setName(account.getName());
            budgetCategoryRepository.save(cat);
        });
    }

    @Transactional
    public void deleteCCPaymentCategory(Account account) {
        if (account.getCcPaymentCategoryId() == null) return;

        budgetCategoryRepository.findById(account.getCcPaymentCategoryId()).ifPresent(cat -> {
            CategoryGroup group = cat.getGroup();
            account.setCcPaymentCategoryId(null);
            budgetCategoryRepository.deleteById(cat.getId());

            List<BudgetCategory> remaining = budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group);
            if (remaining.isEmpty()) {
                categoryGroupRepository.deleteById(group.getId());
            }
        });
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

Run: `./mvnw test -Dtest=CreditCardServiceTest -pl backend`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hbelange/financebudgetapp/service/CreditCardService.java \
        backend/src/test/java/com/hbelange/financebudgetapp/service/CreditCardServiceTest.java
git commit -m "FBA-39: CreditCardService — CC payment category lifecycle"
```

---

## Task 4: AccountService CC Hooks

**Files:**
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/service/AccountService.java`
- Modify: `backend/src/test/java/com/hbelange/financebudgetapp/service/AccountServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add to `AccountServiceTest.java`. First add the mock and update setUp:

```java
@Mock
private CreditCardService creditCardService;
```

Then add the new test methods:

```java
@Test
void create_callsEnsureCCPaymentCategory_whenCreditCard() {
    Account saved = new Account();
    saved.setId(accountId);
    saved.setName("My Visa");
    saved.setType(AccountType.CREDIT_CARD);
    saved.setUserSub(USER_SUB);

    AccountRequest req = new AccountRequest("My Visa", AccountType.CREDIT_CARD);
    when(accountRepository.save(any(Account.class))).thenReturn(saved);

    accountService.create(req, USER_SUB);

    verify(creditCardService).ensureCCPaymentCategory(saved, USER_SUB);
}

@Test
void create_doesNotCallCCService_whenNotCreditCard() {
    AccountRequest req = new AccountRequest("Checking", AccountType.CHECKING);
    when(accountRepository.save(any(Account.class))).thenReturn(account);

    accountService.create(req, USER_SUB);

    verify(creditCardService, never()).ensureCCPaymentCategory(any(), any());
}

@Test
void update_callsSyncCategoryName_whenNameChangedOnCC() {
    account.setType(AccountType.CREDIT_CARD);
    AccountRequest req = new AccountRequest("New Name", AccountType.CREDIT_CARD);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenReturn(account);
    when(accountRepository.findBalanceById(accountId)).thenReturn(BigDecimal.ZERO);

    accountService.update(accountId, req, USER_SUB);

    verify(creditCardService).syncCategoryName(any(Account.class));
}

@Test
void update_doesNotCallSyncCategoryName_whenNameUnchanged() {
    account.setType(AccountType.CREDIT_CARD);
    account.setName("My Visa");
    AccountRequest req = new AccountRequest("My Visa", AccountType.CREDIT_CARD);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenReturn(account);
    when(accountRepository.findBalanceById(accountId)).thenReturn(BigDecimal.ZERO);

    accountService.update(accountId, req, USER_SUB);

    verify(creditCardService, never()).syncCategoryName(any());
}

@Test
void update_callsEnsureCCPaymentCategory_whenTypeChangedToCC() {
    account.setType(AccountType.CHECKING);
    AccountRequest req = new AccountRequest("My Visa", AccountType.CREDIT_CARD);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenReturn(account);
    when(accountRepository.findBalanceById(accountId)).thenReturn(BigDecimal.ZERO);

    accountService.update(accountId, req, USER_SUB);

    verify(creditCardService).ensureCCPaymentCategory(any(Account.class), eq(USER_SUB));
    verify(creditCardService, never()).deleteCCPaymentCategory(any());
}

@Test
void update_callsDeleteCCPaymentCategory_whenTypeChangedAwayFromCC() {
    account.setType(AccountType.CREDIT_CARD);
    AccountRequest req = new AccountRequest("My Account", AccountType.CHECKING);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenReturn(account);
    when(accountRepository.findBalanceById(accountId)).thenReturn(BigDecimal.ZERO);

    accountService.update(accountId, req, USER_SUB);

    verify(creditCardService).deleteCCPaymentCategory(account);
    verify(creditCardService, never()).ensureCCPaymentCategory(any(), any());
}
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `./mvnw test -Dtest=AccountServiceTest -pl backend`

Expected: FAIL — `AccountService` has no `CreditCardService` dependency and no CC hooks.

- [ ] **Step 3: Update AccountService**

Replace `AccountService.java` with:

```java
package com.hbelange.financebudgetapp.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.AccountBalance;
import com.hbelange.financebudgetapp.dto.AccountDTO;
import com.hbelange.financebudgetapp.dto.AccountRequest;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.enums.AccountType;
import com.hbelange.financebudgetapp.repository.AccountRepository;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CreditCardService creditCardService;

    @Autowired
    public AccountService(AccountRepository accountRepository, CreditCardService creditCardService) {
        this.accountRepository = accountRepository;
        this.creditCardService = creditCardService;
    }

    public List<AccountDTO> findAll(String userSub) {
        List<AccountBalance> balances = accountRepository.findBalancesByUserSub(userSub);
        return accountRepository.findByUserSub(userSub)
            .stream()
            .map(a -> {
                BigDecimal balance = balances.stream()
                    .filter(ab -> ab.accountId().equals(a.getId()))
                    .map(AccountBalance::balance)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
                return toDTO(a, balance);
            })
            .toList();
    }

    public AccountDTO create(AccountRequest req, String userSub) {
        Account account = new Account();
        account.setName(req.name());
        account.setType(req.type());
        account.setUserSub(userSub);
        Account saved = accountRepository.save(account);
        if (saved.getType() == AccountType.CREDIT_CARD) {
            creditCardService.ensureCCPaymentCategory(saved, userSub);
        }
        return toDTO(saved, BigDecimal.ZERO);
    }

    public AccountDTO update(UUID id, AccountRequest req, String userSub) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to update this account");
        }

        AccountType oldType = account.getType();
        String oldName = account.getName();

        if (oldType == AccountType.CREDIT_CARD && req.type() != AccountType.CREDIT_CARD) {
            creditCardService.deleteCCPaymentCategory(account);
        }

        account.setName(req.name());
        account.setType(req.type());
        Account saved = accountRepository.save(account);

        if (oldType != AccountType.CREDIT_CARD && req.type() == AccountType.CREDIT_CARD) {
            creditCardService.ensureCCPaymentCategory(saved, userSub);
        } else if (req.type() == AccountType.CREDIT_CARD && !oldName.equals(req.name())) {
            creditCardService.syncCategoryName(saved);
        }

        return toDTO(saved, accountRepository.findBalanceById(id));
    }

    public void delete(UUID id, String userSub) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete this account");
        }

        if (accountRepository.existsTransactionsByAccountId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account has existing transactions");
        }
        accountRepository.deleteById(id);
    }

    private AccountDTO toDTO(Account account, BigDecimal balance) {
        return new AccountDTO(account.getId(), account.getName(), account.getType(), balance);
    }
}
```

- [ ] **Step 4: Update existing AccountServiceTest tests that call `create` or `update`**

The existing `create_savesAndReturnsDto` and `update_*` tests do not stub `creditCardService`, which is fine — Mockito will use a default no-op mock. No changes needed to existing tests.

- [ ] **Step 5: Run tests to confirm they pass**

Run: `./mvnw test -Dtest=AccountServiceTest -pl backend`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hbelange/financebudgetapp/service/AccountService.java \
        backend/src/test/java/com/hbelange/financebudgetapp/service/AccountServiceTest.java
git commit -m "FBA-39: AccountService CC hooks — create/update/type-change triggers"
```

---

## Task 5: CategoryService Guards

**Files:**
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/service/CategoryService.java`
- Modify: `backend/src/test/java/com/hbelange/financebudgetapp/service/CategoryServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add to `CategoryServiceTest.java`. First add mock:

```java
@Mock private AccountRepository accountRepository;
```

And import:

```java
import com.hbelange.financebudgetapp.repository.AccountRepository;
```

Then add test methods:

```java
@Test
void deleteCategory_throwsConflict_whenSystemManaged() {
    when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(accountRepository.existsByCCPaymentCategoryId(categoryId)).thenReturn(true);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> categoryService.deleteCategory(categoryId, USER_SUB));

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    verify(budgetCategoryRepository, never()).deleteById(any());
}

@Test
void renameCategory_throwsConflict_whenSystemManaged() {
    when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(accountRepository.existsByCCPaymentCategoryId(categoryId)).thenReturn(true);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> categoryService.renameCategory(categoryId, new BudgetCategoryRequest("New Name"), USER_SUB));

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    verify(budgetCategoryRepository, never()).save(any());
}

@Test
void deleteGroup_throwsConflict_whenGroupContainsSystemManagedCategory() {
    when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
    when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)).thenReturn(List.of(category));
    when(accountRepository.existsByCCPaymentCategoryId(categoryId)).thenReturn(true);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> categoryService.deleteGroup(groupId, USER_SUB));

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    verify(categoryGroupRepository, never()).deleteById(any());
}
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `./mvnw test -Dtest=CategoryServiceTest -pl backend`

Expected: FAIL — no system-managed checks exist yet, and `AccountRepository` not injected into `CategoryService`.

- [ ] **Step 3: Update CategoryService**

Add `AccountRepository` to the constructor and inject it. Then add guards to `deleteCategory`, `renameCategory`, and `deleteGroup`.

In `CategoryService.java`:

Add to imports:
```java
import com.hbelange.financebudgetapp.repository.AccountRepository;
```

Update constructor:
```java
private final AccountRepository accountRepository;

@Autowired
public CategoryService(CategoryGroupRepository categoryGroupRepository,
                       BudgetCategoryRepository budgetCategoryRepository,
                       BudgetAllocationRepository budgetAllocationRepository,
                       AccountRepository accountRepository) {
    this.categoryGroupRepository = categoryGroupRepository;
    this.budgetCategoryRepository = budgetCategoryRepository;
    this.budgetAllocationRepository = budgetAllocationRepository;
    this.accountRepository = accountRepository;
}
```

In `renameCategory`, add after the ownership check:
```java
if (accountRepository.existsByCCPaymentCategoryId(id)) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "This category is managed by a linked credit card account");
}
```

In `deleteCategory`, add after the ownership check and before the transaction check:
```java
if (accountRepository.existsByCCPaymentCategoryId(id)) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "This category is managed by a linked credit card account");
}
```

In `deleteGroup`, add after the ownership check and before the transaction check:
```java
if (budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)
        .stream().anyMatch(c -> accountRepository.existsByCCPaymentCategoryId(c.getId()))) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "This group contains a category managed by a linked credit card account");
}
```

- [ ] **Step 4: Run tests to confirm they pass**

Run: `./mvnw test -Dtest=CategoryServiceTest -pl backend`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hbelange/financebudgetapp/service/CategoryService.java \
        backend/src/test/java/com/hbelange/financebudgetapp/service/CategoryServiceTest.java
git commit -m "FBA-39: CategoryService guards for system-managed CC Payment categories"
```

---

## Task 6: TransactionRepository New Queries

**Files:**
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/repository/TransactionRepository.java`
- Modify: `backend/src/test/java/com/hbelange/financebudgetapp/repository/TransactionRepositoryTest.java`

- [ ] **Step 1: Write failing tests**

Add to `TransactionRepositoryTest.java`. The setUp already creates `accountA` (CHECKING) and `accountB` (SAVINGS) — add a CC account:

Add to field declarations:
```java
private Account ccAccount;
```

Add to `setUp()` after `accountB` is saved:
```java
ccAccount = new Account();
ccAccount.setName("My Visa");
ccAccount.setType(AccountType.CREDIT_CARD);
ccAccount.setUserSub("auth0|test-user");
ccAccount = accountRepository.save(ccAccount);
```

Add test methods:

```java
// --- sumNetExcludingCCPurchases ---

@Test
void sumNetExcludingCCPurchases_includesNormalTransactions() {
    // accountA (CHECKING) transactions: 50 + 100 - 30 = 120, through Jan 31
    BigDecimal result = transactionRepository.sumNetExcludingCCPurchases(
        LocalDate.of(2026, 1, 31), "auth0|test-user");
    // also includes accountB's Feb transaction but that's after Jan 31, so excluded by date
    assertThat(result).isEqualByComparingTo("120.00");
}

@Test
void sumNetExcludingCCPurchases_excludesCCPurchasesWithCategory() {
    UUID catId = UUID.randomUUID();
    // CC transaction with category — should be excluded
    Transaction ccPurchase = new Transaction();
    ccPurchase.setAccount(ccAccount);
    ccPurchase.setDate(LocalDate.of(2026, 1, 15));
    ccPurchase.setAmount(new BigDecimal("-50.00"));
    ccPurchase.setCategoryId(catId);
    transactionRepository.save(ccPurchase);

    BigDecimal result = transactionRepository.sumNetExcludingCCPurchases(
        LocalDate.of(2026, 1, 31), "auth0|test-user");
    // 120 (from accountA) - 50 (CC) = 70, but CC excluded → still 120
    assertThat(result).isEqualByComparingTo("120.00");
}

@Test
void sumNetExcludingCCPurchases_includesCCTransfersAndUncategorized() {
    // CC transfer (transferId set) — should be included
    Transaction ccTransfer = new Transaction();
    ccTransfer.setAccount(ccAccount);
    ccTransfer.setDate(LocalDate.of(2026, 1, 20));
    ccTransfer.setAmount(new BigDecimal("100.00"));
    ccTransfer.setTransferId(UUID.randomUUID());
    transactionRepository.save(ccTransfer);

    BigDecimal result = transactionRepository.sumNetExcludingCCPurchases(
        LocalDate.of(2026, 1, 31), "auth0|test-user");
    assertThat(result).isEqualByComparingTo("220.00"); // 120 + 100
}

// --- sumForAccount ---

@Test
void sumForAccount_returnsSumOfAllTransactionsUpToDate() {
    // accountA has: 50 (Jan 1), 100 (Jan 10), -30 (Jan 20)
    BigDecimal result = transactionRepository.sumForAccount(
        accountA.getId(), LocalDate.of(2026, 1, 31));
    assertThat(result).isEqualByComparingTo("120.00");
}

@Test
void sumForAccount_respectsCutoffDate() {
    // Only Jan 1 transaction (50) is at or before Jan 1
    BigDecimal result = transactionRepository.sumForAccount(
        accountA.getId(), LocalDate.of(2026, 1, 1));
    assertThat(result).isEqualByComparingTo("50.00");
}

@Test
void sumForAccount_returnsZero_whenNoTransactions() {
    BigDecimal result = transactionRepository.sumForAccount(
        ccAccount.getId(), LocalDate.of(2026, 1, 31));
    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
}
```

Add import at top of TransactionRepositoryTest:
```java
import com.hbelange.financebudgetapp.entity.Transaction;
```
(it's likely already there; add if missing)

- [ ] **Step 2: Run tests to confirm they fail**

Run: `./mvnw test -Dtest=TransactionRepositoryTest -pl backend`

Expected: FAIL — `sumNetExcludingCCPurchases` and `sumForAccount` do not exist.

- [ ] **Step 3: Add new queries to TransactionRepository**

Add to `TransactionRepository.java`:

```java
@Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
       "WHERE t.date <= :lastDay AND t.account.userSub = :userSub " +
       "AND NOT (t.account.type = 'CREDIT_CARD' AND t.categoryId IS NOT NULL AND t.transferId IS NULL)")
BigDecimal sumNetExcludingCCPurchases(@Param("lastDay") LocalDate lastDay, @Param("userSub") String userSub);

@Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.account.id = :accountId AND t.date <= :lastDay")
BigDecimal sumForAccount(@Param("accountId") UUID accountId, @Param("lastDay") LocalDate lastDay);
```

- [ ] **Step 4: Run tests to confirm they pass**

Run: `./mvnw test -Dtest=TransactionRepositoryTest -pl backend`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hbelange/financebudgetapp/repository/TransactionRepository.java \
        backend/src/test/java/com/hbelange/financebudgetapp/repository/TransactionRepositoryTest.java
git commit -m "FBA-39: TransactionRepository — sumNetExcludingCCPurchases + sumForAccount"
```

---

## Task 7: BudgetCategoryViewDTO + BudgetService

**Files:**
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/dto/BudgetCategoryViewDTO.java`
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/service/BudgetService.java`
- Modify: `backend/src/test/java/com/hbelange/financebudgetapp/service/BudgetServiceTest.java`

- [ ] **Step 1: Write failing tests**

Update `BudgetServiceTest.java`. Add mock:

```java
@Mock private AccountRepository accountRepository;
```

Add import:
```java
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.enums.AccountType;
import java.util.Map;
```

Update ALL existing `getBudget_*` tests to replace `sumNetUpToDate` stub with `sumNetExcludingCCPurchases` and add the new account repo stub:

Change this in every existing test that calls `getBudget`:
```java
// OLD — remove this line:
when(transactionRepository.sumNetUpToDate(any(), any())).thenReturn(...);

// NEW — replace with:
when(transactionRepository.sumNetExcludingCCPurchases(any(), any())).thenReturn(...);

// Also add to every existing getBudget test:
when(accountRepository.findByUserSubAndCCPaymentCategoryIdNotNull(USER_SUB)).thenReturn(List.of());
```

Then add new tests:

```java
@Test
void getBudget_showsOwedForCCPaymentCategory() {
    UUID ccAccountId = UUID.randomUUID();
    Account ccAccount = new Account();
    ccAccount.setId(ccAccountId);
    ccAccount.setName("My Visa");
    ccAccount.setType(AccountType.CREDIT_CARD);
    ccAccount.setUserSub(USER_SUB);
    ccAccount.setCcPaymentCategoryId(categoryId);

    when(transactionRepository.sumNetExcludingCCPurchases(any(), any())).thenReturn(BigDecimal.ZERO);
    when(budgetAllocationRepository.sumAssignedUpToMonth(any(), any())).thenReturn(BigDecimal.ZERO);
    when(budgetAllocationRepository.findByMonthAndUserSub(any(), any())).thenReturn(List.of());
    when(transactionRepository.findSpentByCategoryForMonth(any(), any(), any())).thenReturn(List.of());
    when(accountRepository.findByUserSubAndCCPaymentCategoryIdNotNull(USER_SUB))
        .thenReturn(List.of(ccAccount));
    when(transactionRepository.sumForAccount(eq(ccAccountId), any()))
        .thenReturn(new BigDecimal("-120.00")); // CC has $120 balance owed
    when(categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(USER_SUB)).thenReturn(List.of(group));
    when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)).thenReturn(List.of(category));

    BudgetViewDTO result = budgetService.getBudget("2026-05", USER_SUB);

    var cat = result.groups().get(0).categories().get(0);
    assertTrue(cat.systemManaged());
    assertEquals(new BigDecimal("120.00"), cat.available()); // owed = -(-120) = 120
    assertEquals(BigDecimal.ZERO, cat.assigned());
    assertEquals(BigDecimal.ZERO, cat.spent());
}

@Test
void getBudget_showsZeroOwed_whenCCHasNoBalance() {
    UUID ccAccountId = UUID.randomUUID();
    Account ccAccount = new Account();
    ccAccount.setId(ccAccountId);
    ccAccount.setName("My Visa");
    ccAccount.setType(AccountType.CREDIT_CARD);
    ccAccount.setUserSub(USER_SUB);
    ccAccount.setCcPaymentCategoryId(categoryId);

    when(transactionRepository.sumNetExcludingCCPurchases(any(), any())).thenReturn(BigDecimal.ZERO);
    when(budgetAllocationRepository.sumAssignedUpToMonth(any(), any())).thenReturn(BigDecimal.ZERO);
    when(budgetAllocationRepository.findByMonthAndUserSub(any(), any())).thenReturn(List.of());
    when(transactionRepository.findSpentByCategoryForMonth(any(), any(), any())).thenReturn(List.of());
    when(accountRepository.findByUserSubAndCCPaymentCategoryIdNotNull(USER_SUB))
        .thenReturn(List.of(ccAccount));
    when(transactionRepository.sumForAccount(eq(ccAccountId), any()))
        .thenReturn(new BigDecimal("50.00")); // positive balance (overpaid) → owed = 0
    when(categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(USER_SUB)).thenReturn(List.of(group));
    when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)).thenReturn(List.of(category));

    BudgetViewDTO result = budgetService.getBudget("2026-05", USER_SUB);

    var cat = result.groups().get(0).categories().get(0);
    assertTrue(cat.systemManaged());
    assertEquals(BigDecimal.ZERO, cat.available());
}

@Test
void getBudget_systemManaged_falseForNormalCategories() {
    when(transactionRepository.sumNetExcludingCCPurchases(any(), any())).thenReturn(BigDecimal.ZERO);
    when(budgetAllocationRepository.sumAssignedUpToMonth(any(), any())).thenReturn(BigDecimal.ZERO);
    when(budgetAllocationRepository.findByMonthAndUserSub(any(), any())).thenReturn(List.of());
    when(transactionRepository.findSpentByCategoryForMonth(any(), any(), any())).thenReturn(List.of());
    when(accountRepository.findByUserSubAndCCPaymentCategoryIdNotNull(USER_SUB)).thenReturn(List.of());
    when(categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(USER_SUB)).thenReturn(List.of(group));
    when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)).thenReturn(List.of(category));

    BudgetViewDTO result = budgetService.getBudget("2026-05", USER_SUB);

    var cat = result.groups().get(0).categories().get(0);
    assertFalse(cat.systemManaged());
}
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `./mvnw test -Dtest=BudgetServiceTest -pl backend`

Expected: FAIL — `BudgetCategoryViewDTO` missing `systemManaged`, `AccountRepository` not injected in `BudgetService`.

- [ ] **Step 3: Update BudgetCategoryViewDTO**

Replace `BudgetCategoryViewDTO.java`:

```java
package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetCategoryViewDTO(
    UUID id,
    String name,
    BigDecimal assigned,
    BigDecimal spent,
    BigDecimal available,
    boolean systemManaged
) {}
```

- [ ] **Step 4: Update BudgetService**

Replace `BudgetService.java`:

```java
package com.hbelange.financebudgetapp.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.AllocationRequest;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.dto.BudgetCategoryViewDTO;
import com.hbelange.financebudgetapp.dto.BudgetGroupDTO;
import com.hbelange.financebudgetapp.dto.BudgetViewDTO;
import com.hbelange.financebudgetapp.dto.CategorySpent;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.BudgetAllocationRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

@Service
public class BudgetService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Autowired
    public BudgetService(
            CategoryGroupRepository categoryGroupRepository,
            BudgetCategoryRepository budgetCategoryRepository,
            BudgetAllocationRepository budgetAllocationRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository) {
        this.categoryGroupRepository = categoryGroupRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    public BudgetViewDTO getBudget(String monthParam, String userSub) {
        YearMonth month = parseMonth(monthParam);
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();

        BigDecimal totalNet = transactionRepository.sumNetExcludingCCPurchases(lastDay, userSub);
        BigDecimal totalAssigned = budgetAllocationRepository.sumAssignedUpToMonth(firstDay, userSub);
        BigDecimal readyToAssign = totalNet.subtract(totalAssigned);

        Map<UUID, UUID> ccPaymentCategoryToAccount = accountRepository
            .findByUserSubAndCCPaymentCategoryIdNotNull(userSub)
            .stream()
            .collect(Collectors.toMap(a -> a.getCcPaymentCategoryId(), a -> a.getId()));

        Map<UUID, BigDecimal> assignedByCategory = budgetAllocationRepository.findByMonthAndUserSub(firstDay, userSub)
            .stream().collect(Collectors.toMap(a -> a.getCategoryId(), a -> a.getAssigned()));

        Map<UUID, BigDecimal> spentByCategory = transactionRepository.findSpentByCategoryForMonth(firstDay, lastDay, userSub)
            .stream().collect(Collectors.toMap(CategorySpent::categoryId, CategorySpent::spent));

        List<BudgetGroupDTO> groups = categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(userSub).stream()
            .map(g -> {
                List<BudgetCategoryViewDTO> cats = budgetCategoryRepository
                    .findByGroupOrderBySortOrderAsc(g).stream()
                    .map(c -> buildCategoryView(c, ccPaymentCategoryToAccount, assignedByCategory, spentByCategory, lastDay))
                    .collect(Collectors.toList());
                return new BudgetGroupDTO(g.getId(), g.getName(), cats);
            }).collect(Collectors.toList());

        return new BudgetViewDTO(readyToAssign, groups);
    }

    private BudgetCategoryViewDTO buildCategoryView(BudgetCategory c,
            Map<UUID, UUID> ccPaymentCategoryToAccount,
            Map<UUID, BigDecimal> assignedByCategory,
            Map<UUID, BigDecimal> spentByCategory,
            LocalDate lastDay) {
        if (ccPaymentCategoryToAccount.containsKey(c.getId())) {
            UUID accountId = ccPaymentCategoryToAccount.get(c.getId());
            BigDecimal balance = transactionRepository.sumForAccount(accountId, lastDay);
            BigDecimal owed = balance.negate().max(BigDecimal.ZERO);
            return new BudgetCategoryViewDTO(c.getId(), c.getName(), owed, BigDecimal.ZERO, owed, true);
        }
        BigDecimal assigned = assignedByCategory.getOrDefault(c.getId(), BigDecimal.ZERO);
        BigDecimal spent = spentByCategory.getOrDefault(c.getId(), BigDecimal.ZERO);
        return new BudgetCategoryViewDTO(c.getId(), c.getName(), assigned, spent, assigned.add(spent), false);
    }

    @Transactional
    public void upsertAllocation(AllocationRequest req, String userSub) {
        YearMonth month = parseMonth(req.month());
        BudgetCategory category = budgetCategoryRepository.findById(req.categoryId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to allocate to this category");
        }
        budgetAllocationRepository.upsert(req.categoryId(), month.atDay(1), req.assigned());
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month format, expected yyyy-MM");
        }
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

Run: `./mvnw test -Dtest=BudgetServiceTest -pl backend`

Expected: PASS

- [ ] **Step 6: Run all backend tests**

Run: `./mvnw test -pl backend`

Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/hbelange/financebudgetapp/dto/BudgetCategoryViewDTO.java \
        backend/src/main/java/com/hbelange/financebudgetapp/service/BudgetService.java \
        backend/src/test/java/com/hbelange/financebudgetapp/service/BudgetServiceTest.java
git commit -m "FBA-39: BudgetService — exclude CC purchases from readyToAssign, dynamic CC Payment display"
```

---

## Task 8: Frontend — systemManaged Display

**Files:**
- Modify: `frontend/src/app/core/services/budget.service.ts`
- Modify: `frontend/src/app/budget/budget.component.html`

- [ ] **Step 1: Add `systemManaged` to BudgetCategory interface**

In `budget.service.ts`, update the `BudgetCategory` interface:

```typescript
export interface BudgetCategory {
  id: string;
  name: string;
  assigned: number;
  spent: number;
  available: number;
  systemManaged: boolean;
}
```

- [ ] **Step 2: Update the category row template**

In `budget.component.html`, replace the `@for (cat of group.categories...)` row block. Find this block:

```html
@for (cat of group.categories; track cat.id) {
  <tr cdkDrag [cdkDragData]="cat">
    <td class="col-drag">
      <span cdkDragHandle class="drag-handle" aria-label="Drag to reorder category">
        <mat-icon>drag_indicator</mat-icon>
      </span>
    </td>
    <td class="col-name">{{ cat.name }}</td>
    <td class="col-number col-assigned">
      <input
        class="assigned-input"
        type="text"
        inputmode="decimal"
        [value]="cat.assigned | currency"
        (focus)="onAssignedFocus($event, cat)"
        (blur)="onAssignedBlur(group.id, cat.id, $event)"
        (keydown.enter)="onAssignedEnter($event)"
      />
    </td>
    <td class="col-number spent">{{ abs(cat.spent) | currency }}</td>
    <td class="col-number col-available" [class.positive]="cat.available >= 0" [class.negative]="cat.available < 0">
      {{ cat.available | currency }}
    </td>
    <ng-template cdkDragPreview>
      <div class="category-drag-preview">{{ cat.name }}</div>
    </ng-template>
    <td class="col-actions">
      <button mat-icon-button [attr.aria-label]="'Rename ' + cat.name"
        (click)="openRenameCategory(cat)">
        <mat-icon>edit</mat-icon>
      </button>
      <button mat-icon-button [attr.aria-label]="'Delete ' + cat.name"
        (click)="confirmDeleteCategory(cat)">
        <mat-icon>delete</mat-icon>
      </button>
    </td>
  </tr>
}
```

Replace it with:

```html
@for (cat of group.categories; track cat.id) {
  <tr cdkDrag [cdkDragData]="cat" [cdkDragDisabled]="cat.systemManaged">
    <td class="col-drag">
      @if (!cat.systemManaged) {
        <span cdkDragHandle class="drag-handle" aria-label="Drag to reorder category">
          <mat-icon>drag_indicator</mat-icon>
        </span>
      }
    </td>
    <td class="col-name">{{ cat.name }}</td>
    @if (cat.systemManaged) {
      <td class="col-number col-owed col-owed-span" colspan="3">
        Owed: {{ cat.available | currency }}
      </td>
    } @else {
      <td class="col-number col-assigned">
        <input
          class="assigned-input"
          type="text"
          inputmode="decimal"
          [value]="cat.assigned | currency"
          (focus)="onAssignedFocus($event, cat)"
          (blur)="onAssignedBlur(group.id, cat.id, $event)"
          (keydown.enter)="onAssignedEnter($event)"
        />
      </td>
      <td class="col-number spent">{{ abs(cat.spent) | currency }}</td>
      <td class="col-number col-available" [class.positive]="cat.available >= 0" [class.negative]="cat.available < 0">
        {{ cat.available | currency }}
      </td>
    }
    <ng-template cdkDragPreview>
      <div class="category-drag-preview">{{ cat.name }}</div>
    </ng-template>
    <td class="col-actions">
      @if (!cat.systemManaged) {
        <button mat-icon-button [attr.aria-label]="'Rename ' + cat.name"
          (click)="openRenameCategory(cat)">
          <mat-icon>edit</mat-icon>
        </button>
        <button mat-icon-button [attr.aria-label]="'Delete ' + cat.name"
          (click)="confirmDeleteCategory(cat)">
          <mat-icon>delete</mat-icon>
        </button>
      }
    </td>
  </tr>
}
```

- [ ] **Step 3: Build the frontend**

Run: `cd frontend && npx ng build`

Expected: BUILD SUCCESS — no TypeScript errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/core/services/budget.service.ts \
        frontend/src/app/budget/budget.component.html
git commit -m "FBA-39: Frontend — systemManaged categories show read-only Owed value"
```

---

## Final Verification

- [ ] **Step 1: Run all backend tests**

Run: `./mvnw test -pl backend`

Expected: All tests PASS

- [ ] **Step 2: Start the application and smoke test**

Start backend: `./mvnw spring-boot:run`
Start frontend: `cd frontend && npx ng serve`

Manual checks:
1. Create a new CREDIT_CARD account — "Credit Card Payments" group appears in budget with a category named after the account
2. Record a CC purchase with a category — spending category available decreases, CC Payment owed increases, readyToAssign unchanged
3. Record a transfer from checking to the CC account — CC Payment owed decreases proportionally
4. Rename the CC account — CC Payment category name updates
5. Try to rename/delete the CC Payment category from the budget UI — 409 error shown
6. Create a second CC account — its payment category is added to the existing "Credit Card Payments" group
7. Delete one CC account (no transactions) — its payment category is deleted; group remains since other CC account exists
8. Delete the other CC account (no transactions) — its payment category is deleted; "Credit Card Payments" group is also deleted

- [ ] **Step 3: Create PR and move Jira ticket to In Review**
