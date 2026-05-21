# Finance Budget App

A YNAB-style personal finance and budget tracker. Zero-based budgeting: every dollar gets assigned to a category before you spend it.

Built as a personal project to replace spreadsheets and learn full-stack Java + Angular end to end.

---

## What it does

- **Accounts** — track checking, savings, credit cards, and cash accounts; balance computed live from transactions
- **Transaction ledger** — paginated and filterable by account and month; inflows/outflows color-coded
- **Budget view** — assign money to categories each month; Ready to Assign header shows unallocated dollars (turns red when overspent)
- **Category management** — create groups and categories, rename or delete them inline
- **Dashboard** — monthly summary: net worth, income, spending, and a spending-by-category breakdown with progress bars
- **Auth** — Auth0-managed login; Spring Boot validates JWTs as an OAuth2 resource server

---

## Stack

| Layer | Technology | Why |
|---|---|---|
| Backend | Java 21 + Spring Boot 3.x | Familiar, production-grade, strong type system for financial logic |
| Build | Maven | Conventional Java build; Flyway migration support built in |
| Database | PostgreSQL 16 (Docker) | Best ecosystem, scales further than I'll ever need, free-tier hostable |
| Schema migrations | Flyway | Reproducible, versioned schema — no manual SQL on every setup |
| Frontend | Angular 21 (standalone) | Component model fits the domain; Angular Material covers the UI kit |
| Auth | Auth0 | Managed auth — not rolling my own session/token handling |
| Deployment | Render (backend + DB), Vercel (frontend) | Free tier, zero ops overhead |

---

## Architecture

```
[Browser] --HTTPS--> [Angular on Vercel]
                             |
                         /api proxy
                             |
                     [Spring Boot on Render] --> [PostgreSQL on Render]
                             |
                         [Auth0 JWT validation]
```

Single server, single database, managed auth. No queues, no cache, no microservices. Designed for one user, deliberately boring.

---

## Key technical decisions

**Money is `NUMERIC(15,2)`, never `FLOAT`.**
Floating-point arithmetic is wrong for money. `0.1 + 0.2` in IEEE 754 is not `0.3`. All amounts are stored as fixed-point and returned as `BigDecimal` in Java.

**Account balance is computed, never stored.**
Storing a balance creates a sync problem: every transaction mutation has to also update the account row atomically, or you get drift. Instead, `balance = SUM(transactions.amount)` is computed on every read. Correct by construction, no two-phase update needed.

**Flyway for schema migrations.**
A fresh `./mvnw spring-boot:run` applies all migrations automatically. No README step that says "also run this SQL file manually." The schema is versioned alongside the code.

**`month` stored as a `DATE` with a `CHECK` constraint.**
All budget allocations reference a month as `DATE_TRUNC('month', value)` — the first of the month only. A check constraint enforces this at the database level so no application bug can insert a mid-month date.

**No NgRx on the frontend.**
A single `BudgetStateService` with a `BehaviorSubject<string>` holds the selected month. Every component that cares subscribes to it. For a single-user app with no complex shared mutations, adding a full Redux-style store would be over-engineering. Three months in, this is still the right call.

**Optimistic updates for budget allocations.**
When a user edits an allocation, the UI updates instantly and the API call fires in the background. If the server returns an error, the change is reverted. This makes the budget view feel instant without pessimistic locking complexity.

**Standalone Angular components throughout.**
No NgModules. Each component declares its own imports. Easier to read, easier to lazy-load, and the direction Angular is heading anyway.

---

## What I learned

- **Zero-based budgeting is harder to model than it looks.** "Ready to Assign" — the dollars available to budget — is `cumulative_income - total_assigned`. It accumulates across all past months, not just the current one. Getting this calculation right (and keeping it consistent with per-category `available` values) took a few iterations.

- **Spring Data JPA is great until you need aggregate queries.** For the budget view and reports, I needed `SUM` across multiple tables. Rather than forcing that into Spring Data interfaces, I used `EntityManager` with JPQL directly. One data access pattern per project is enough.

- **Angular Material's reactive form model pays off.** The budget inline-edit fields, dialog forms, and date pickers all share the same `FormGroup` / `FormControl` pattern. Validation, disabled states, and error messages come for free once you understand the model.

- **Deployment is where the "it works locally" assumptions break.** CORS headers, database connection string format, Auth0 callback URLs, environment variable injection — none of this is hard, but all of it has to be right at the same time. Building the walking skeleton first (empty app, deployed, connected to DB) meant I hit these early rather than at the end.

---

## Running locally

**Prerequisites:** Java 21, Node 20+, Docker

```bash
# Start PostgreSQL
docker run -d --name budget-postgres \
  -e POSTGRES_DB=budget -e POSTGRES_USER=budget -e POSTGRES_PASSWORD=budget \
  -p 5432:5432 postgres:16

# Backend (applies Flyway migrations automatically)
./mvnw spring-boot:run

# Frontend (proxies /api to localhost:8080)
cd frontend && npm install && npx ng serve
```

Open `http://localhost:4200`.

**Convenience scripts:**
```bash
./start.sh   # starts backend + frontend together
./stop.sh    # stops them
```

**Access the database directly:**
```bash
docker exec -it budget-postgres psql -U budget
```

---

## Project structure

```
backend/
  src/main/java/com/hbelange/financebudgetapp/
    controller/     # REST endpoints
    service/        # Business logic
    repository/     # JPA + EntityManager queries
    entity/         # JPA entities
    dto/            # Request/response shapes
    enums/          # AccountType, etc.
  src/main/resources/
    db/migration/   # Flyway V1-V9 SQL migrations

frontend/src/app/
  accounts/         # Accounts list + dialog
  transactions/     # Transaction ledger
  budget/           # Budget view + category management
  dashboard/        # Monthly summary
  core/services/    # AccountService, BudgetService, etc.
  shared/           # Shared components
```

---

## What's next

- **Deployment** — backend on Render, frontend on Vercel, Auth0 wired to production URLs
- **Database backup** — daily pg_dump once the production instance is settled
