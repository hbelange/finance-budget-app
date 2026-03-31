# Finance Budget App — MVP Plan

## Context

Building a YNAB-style personal finance and budget tracker from scratch. The repo is currently empty (only README + LICENSE). The goal is a working MVP with: account/transaction tracking, monthly budget categories with zero-based budgeting ("assign every dollar"), and a summary dashboard.

**Stack**: Java 21 + Spring Boot 3.x + Maven + PostgreSQL (Docker) + Angular (standalone components) + Angular Material. No auth (single user).

---

## Project Layout

```
finance-budget-app/
├── backend/                          # Spring Boot Maven project
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/budget/app/
│       │   ├── BudgetApplication.java
│       │   ├── account/              # Account entity, repo, service, controller
│       │   ├── transaction/          # Transaction entity, repo, service, controller
│       │   ├── budget/               # CategoryGroup, BudgetCategory, BudgetAllocation + budget logic
│       │   └── report/               # Dashboard aggregation
│       └── resources/
│           ├── application.properties
│           └── db/migration/         # Flyway SQL migrations
└── frontend/                         # Angular project
    ├── proxy.conf.json               # /api -> localhost:8080
    └── src/app/
        ├── core/services/            # account, transaction, budget, report services
        ├── layout/                   # MatSidenav shell + routing
        ├── accounts/
        ├── transactions/
        ├── budget/
        └── dashboard/
```

---

## Domain Model

### Entities (backend)

**Account** — `id` (UUID), `name`, `type` (CHECKING/SAVINGS/CREDIT_CARD/CASH)
- Balance is **computed** from transactions (never stored) — avoids sync bugs

**Transaction** — `id`, `accountId`, `date`, `payee`, `categoryId` (nullable), `amount` (positive=inflow, negative=outflow), `memo`, `cleared`

**CategoryGroup** — `id`, `name`, `sortOrder`

**BudgetCategory** — `id`, `groupId`, `name`, `sortOrder`

**BudgetAllocation** — `id`, `categoryId`, `month` (DATE, first-of-month), `assigned` (NUMERIC 15,2)
- Unique constraint on `(category_id, month)` — enables upsert semantics

### Key Calculations

**Ready to Assign** (month M):
```
total_income   = SUM(amount) WHERE amount > 0 AND date <= last day of M
total_assigned = SUM(assigned) WHERE month <= first day of M
ready_to_assign = total_income - total_assigned
```

**Per-category Available**:
```
available = allocation.assigned + SUM(transaction.amount WHERE category=X AND month=M)
```
Negative = over-budget (red), positive = money remaining (green).

---

## Database Migrations (Flyway)

Files in `backend/src/main/resources/db/migration/`:

```
V1__create_accounts.sql
V2__create_transactions.sql
V3__create_category_groups.sql
V4__create_budget_categories.sql
V5__create_budget_allocations.sql
V6__seed_default_category_groups.sql
```

Rules:
- UUIDs as PKs with `DEFAULT gen_random_uuid()`
- All money as `NUMERIC(15,2)` — never FLOAT
- `month` stored as `DATE` with check: `month = DATE_TRUNC('month', month)`
- Indexes on: `transaction(account_id)`, `transaction(category_id, date)`, `budget_allocation(category_id, month)`

---

## REST API (all prefixed `/api`)

### Accounts
- `GET /api/accounts` — list with computed balance
- `POST /api/accounts` — create
- `PUT /api/accounts/{id}` — update
- `DELETE /api/accounts/{id}` — delete (reject if transactions exist)

### Transactions
- `GET /api/transactions?accountId=&month=` — paginated list
- `POST /api/transactions` — create
- `PUT /api/transactions/{id}` — edit
- `DELETE /api/transactions/{id}` — delete

### Budget Categories
- `GET /api/category-groups` — all groups with nested categories
- `POST /api/category-groups` — create group
- `POST /api/category-groups/{groupId}/categories` — create category
- `PUT /api/categories/{id}` — rename
- `DELETE /api/categories/{id}` — delete

### Budget (Monthly)
- `GET /api/budget?month=2026-03` — full budget view: groups → categories → assigned/spent/available
- `PUT /api/budget/allocations` — upsert `{categoryId, month, assigned}`
- `GET /api/budget/ready-to-assign?month=2026-03` — `{readyToAssign: 500.00}`

### Reports
- `GET /api/reports/dashboard?month=2026-03` — net worth, income, spent, top categories
- `GET /api/reports/spending-by-category?month=2026-03` — category breakdown

Use record-based DTOs (not entities) in controllers. `BudgetService` handles all ready-to-assign and available calculations.

---

## Frontend (Angular)

### Routes
```
/accounts                     -> AccountsListComponent
/accounts/:id/transactions    -> TransactionLedgerComponent
/budget                       -> BudgetComponent  (default)
/dashboard                    -> DashboardComponent
```

All routes use `loadComponent` (lazy). Shell (`MatSidenav` + `MatToolbar`) is eager.

### Key UI Components
- **Shell**: `MatSidenav` nav, month selector (`MatSelect`) in toolbar driving a `BehaviorSubject<string>` shared via `BudgetStateService`
- **Accounts**: `MatTable` + `MatDialog` for create/edit form
- **Transactions**: `MatTable` with `MatPaginator`, `MatSort`, `MatDatepicker`, category `MatSelect`
- **Budget**: "Ready to Assign" `MatCard` (red if negative) + category groups as `MatExpansionPanel` rows with inline `MatInput` for assigned amount; blur triggers upsert
- **Dashboard**: 4 summary `MatCard` tiles + spending by category as `MatProgressBar` list (no chart library needed for MVP)

### State Management
No NgRx. Each component fetches on `ngOnInit`. Month state lives in `BudgetStateService` (`BehaviorSubject`). Mutations trigger a re-fetch.

### Dev Proxy (`frontend/proxy.conf.json`)
```json
{ "/api": { "target": "http://localhost:8080", "secure": false } }
```
Referenced in `angular.json` under `serve.options.proxyConfig`. No CORS config needed during development.

---

## Development Commands

```bash
# Start PostgreSQL
docker run -d --name budget-postgres \
  -e POSTGRES_DB=budget -e POSTGRES_USER=budget -e POSTGRES_PASSWORD=budget \
  -p 5432:5432 postgres:16

# Backend (from /backend)
./mvnw spring-boot:run

# Frontend (from /frontend)
npm install && ng serve

# Run single test
./mvnw test -Dtest=ClassName#methodName
```

---

## Build Order

1. Flyway migrations (V1–V5) — verify schema on startup
2. Accounts API — entity, repo, service (balance calc), controller
3. Transactions API — with account + month filter queries
4. Budget API — categories, allocations, ready-to-assign endpoint
5. Reports API — dashboard aggregation
6. Angular shell — routing + sidenav
7. Accounts page — list + create dialog
8. Transaction ledger — filtered by account + month
9. Budget view — inline editing, ready-to-assign header (most complex UI)
10. Dashboard — read-only summary

---

## Critical Files

- `backend/pom.xml` — must be correct before any code compiles
- `backend/src/main/resources/db/migration/` — schema source of truth; get right first
- `backend/src/main/java/com/budget/app/budget/BudgetService.java` — ready-to-assign and available calculations
- `frontend/proxy.conf.json` — required for end-to-end dev connectivity
- `frontend/src/app/budget/budget.component.ts` — most complex UI component
