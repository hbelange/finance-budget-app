# Finance Budget App

A personal finance and budget tracker. Zero-based budgeting, so every dollar gets assigned to a category before you spend it.

Built as a personal project to replace spreadsheets and learn full-stack Spring Boot + Angular end to end.

---

## Live Demo

**[finance-budget-app-swart.vercel.app](https://finance-budget-app-swart.vercel.app)**

| | |
|---|---|
| Email | `demo@fba.example` |
| Password | `fbaDem0!` |

Shared demo account, seeded with 3 months of sample data. Resets hourly, so don't be surprised if your changes disappear — that's expected. The backend is on Render's free tier and may take a few seconds to wake up on first load.

![Budget view walkthrough](docs/images/demo.gif)

---

## What it does

- **Accounts** — track checking, savings, credit cards, and cash accounts; balance computed live from transactions
- **Transaction ledger** — paginated and filterable by account and month; inflows/outflows color-coded
- **Budget view** — assign money to categories each month; Ready to Assign header shows unallocated dollars (turns red when overspent)
- **Goal Setting** - set goals for existing categories each month. Goals can be refill up to, or accumulate/set aside another X dollars
- **Category management** — create groups and categories, rename or delete them inline
- **Dashboard** — monthly summary: net worth, income, spending, and a spending-by-category breakdown with progress bars
- **Auth** — Auth0-managed login; Spring Boot validates JWTs as an OAuth2 resource server

---

## Stack

| Layer | Technology | Why |
|---|---|---|
| Backend | Java 21 + Spring Boot 3.x | Familiar, production-grade, strong type system for financial logic |
| Build | Maven | Conventional Java build |
| Database | PostgreSQL (Neon DB) | Free-tier hostable |
| Schema migrations | Flyway | Reproducible, versioned schema — no manual SQL on every setup |
| Frontend | Angular 21 | Strongly typed, object-oriented w/ TypeScript, similar architecture to Spring Boot (i.e. Dependency Injection) |
| Auth | Auth0 | Managed auth — not rolling my own session/token handling |
| Deployment | Render (backend), Vercel (frontend) | Free tier, zero ops overhead |

---

## Architecture

```
[Browser] --HTTPS--> [Angular on Vercel]
                             |
                         /api proxy
                             |
                     [Spring Boot on Render] --> [PostgreSQL on Neon]
                             |
                         [Auth0 JWT validation]
```

**Auth:** Auth0 handles signup, login, password resets, and token issuance. Rolling my own auth means owning password hashing, reset-token flows, and session security — a lot of surface area for a personal app where auth isn't the main learning goal. Auth0's free tier covers this scale entirely.

**Stateless JWT** The backend validates a bearer token on every request instead of maintaining session state in a store. This keeps the API server stateless — no session table, no sticky sessions, no shared session cache to provision if it ever scaled beyond one instance. The tradeoff is that revoking a token before it expires isn't instant, which is an acceptable risk for a personal project.

**Render + Vercel** Render and Vercel's free tiers push a `git push` straight to a running URL with managed TLS and restarts. The cost is a cold-start delay after 15 minutes of inactivity on Render's free plan — traded deliberately for zero ops overhead on a project where learning Spring Boot + Angular was the main priority.

---

## What I learned

- **Zero-based budgeting is harder to model than it looks.** "Ready to Assign" — the dollars available to budget — is `cumulative_income - total_assigned`. It accumulates across all past months, not just the current one. Getting this calculation right (and keeping it consistent with per-category `available` values) took a few attempts.

- **Spring Data JPA is great until you need more complex queries.** For the budget view and reports, I needed joined queries across several tables. Rather than forcing that into Spring Data interfaces, I used `EntityManager` with JPQL directly. Using an alternative data access object (DAO) such as MyBatis, would allow me to write my own SQL by hand, which would be better suited to complex queries. 

- **Angular Material's reactive form model:** The budget inline-edit fields, dialog forms, and date pickers all share the same `FormGroup` / `FormControl` pattern. Validation, disabled states, and error messages were simple to implement.

- **Deployment can take some time to set up** CORS headers, database connection string format, Auth0 callback URLs, environment variable injection — none of this is hard, but all of it has to be right. Building the walking skeleton first (empty app, deployed, connected to DB) meant I hit these early rather than at the end, when mental fatigue was inenvitable.
