# Finance Budget App — Playbook Mapping

Mapping the finance-budget-app project to each section of *The Ultimate Personal Project Playbook* by Arjay.

---

## 1. Picking a Project

### Why Most Personal Projects Die

The playbook identifies three killers: too ambitious, no definition of "done," and solving a problem you don't have.

**This project:**
- **Ambition:** Scoped appropriately. It's not "build a bank." It's a single-user budget tracker with a concrete feature list of 11 tickets.
- **Definition of done:** Each ticket is binary. The progress table in PLAN.md gives a clear finish line. This is done well.
- **Solving a real problem:** Personal finance tracking is a genuine everyday annoyance. You are the target user. This is the gold standard origin per the playbook.

### Where Good Ideas Come From

Playbook category: **Annoyances in your own life.** You have at least one real user (yourself), and built-in motivation to finish it because you actually want to use the app. YNAB-style budgeting is a well-understood domain — you understand it better than most engineers.

### Filtering Your Ideas

| Filter | Assessment |
|--------|------------|
| One sentence | "A YNAB-style zero-based budget tracker for personal finance." ✓ |
| Would I use it | Yes — single-user, no auth, designed to be used daily. ✓ |
| Am I the user | Yes. Single-user by design. You are your own beta tester. ✓ |
| What does done look like | "I can track accounts, transactions, budget allocations, and see a monthly dashboard." ✓ |

**Verdict: Passed the filter cleanly.**

---

## 2. Defining Scope

### The MVP

The playbook defines MVP as: *the smallest possible thing I can build that proves the idea works and that I'd actually use.*

**This project's MVP is well-defined:** The 11 tickets map directly to a working zero-based budgeting loop:
- Accounts (FBA-3, FBA-8) → where money lives
- Transactions (FBA-4, FBA-9) → where money moves
- Budget (FBA-5, FBA-10) → where you assign it
- Dashboard (FBA-6, FBA-11) → how you understand it

No auth, no multi-user, no mobile app, no real-time sync, no notifications. Good cuts.

### Functional Requirements

The project's functional requirements as implemented:

- I can create and manage bank accounts (CRUD)
- I can record transactions (CRUD, paginated, filterable by month)
- I can assign money to budget categories month-by-month
- I can see how much is "Ready to Assign" and track category spending
- I can view a monthly dashboard with net worth, income, spending, and top categories
- I can navigate between months with a shared month selector

That's roughly 6 core loops — right at the playbook's ~7-bullet upper bound for an MVP.

Category management UI is complete (FBA-23). The Angular budget view supports creating, renaming, and deleting category groups and categories, backed by the existing API endpoints.

**What was explicitly cut (correctly):**
- Multi-user support
- Mobile app (responsive web is enough)
- Real-time sync
- Email notifications
- Export / import
- Investment tracking beyond account balances

### Non-Functional Requirements

| Requirement | Playbook Guidance | This Project |
|-------------|-------------------|--------------|
| Scalability | 1 user is fine | Single-user, no scaling needed ✓ |
| Latency | Under 2s is fine | Local + paginated queries ✓ |
| Availability | "It's up when I check it" | Local dev; no production deploy yet |
| Durability | Don't lose your data | PostgreSQL with Docker; no backup configured yet (gap) |
| Security | No plaintext passwords, HTTPS | Auth0 managed auth complete (FBA-24); HTTPS enforced by hosting provider on deploy |
| Observability | You'll find out when it breaks | Standard Spring Boot logs ✓ |

**Gap identified:** No database backup strategy. The playbook explicitly calls this out as one of only two non-functional requirements that genuinely matter at personal project scale ("If you spend three months building a journaling app and lose all your entries..."). Worth setting up a simple pg_dump cron before deploying.

---

## 3. Planning the Build

### Milestones

The 11 Jira tickets are the milestones. Each is binary: merged to main or not. This matches the playbook's recommendation exactly — no dates, just concrete demonstrable states.

| Ticket | Milestone State | Status |
|--------|-----------------|--------|
| FBA-1 | Spring Boot + Angular skeleton runs locally | Done |
| FBA-2 | Database schema exists with seed data | Done |
| FBA-3 | Accounts API works end-to-end | Done |
| FBA-4 | Transactions API works end-to-end | Done |
| FBA-5 | Budget API with allocations and Ready-to-Assign | Done |
| FBA-6 | Reports API for dashboard and spending breakdown | Done |
| FBA-7 | Angular shell with routing and month selector | Done |
| FBA-8 | Accounts page with create/edit dialog | Done |
| FBA-9 | Transaction ledger with filtering and pagination | Done |
| FBA-10 | Budget view with inline allocation editing | Done |
| FBA-11 | Dashboard with summary tiles and spending chart | Done |
| FBA-23 | Category Management UI — create/rename/delete groups and categories | Done |
| FBA-24 | Authentication — Auth0 managed auth (Spring Security + Angular SDK) | Done |
| FBA-25 | Deploy — Render (backend + DB) + Vercel (frontend) | To Do |

**13 of 14 milestones complete.** One remaining before the project is at the "shipped" threshold.

### The Walking Skeleton

The playbook says: build the walking skeleton first — every layer connected, even if nothing is useful.

**This project's walking skeleton:**
- **FBA-1:** Spring Boot + Angular scaffolding, Maven build, Angular CLI, Docker Postgres
- **FBA-7:** Angular shell calls `GET /api/health` and renders a page

Every wire (frontend → backend → database) was connected in FBA-1/FBA-7 before any feature was built. Subsequent tickets added flesh to a body that already walked. This was done correctly.

### Picking a Tech Stack

The playbook says: use boring technology. Boring = large community, mountains of docs, what companies actually hire for.

| Layer | Choice | Playbook Score |
|-------|--------|----------------|
| Backend | Java 21 + Spring Boot 3 + Maven | Very boring — industrial standard ✓ |
| Frontend | Angular 21 + Angular Material | Boring — enterprise standard ✓ |
| Database | PostgreSQL 16 (Docker) | Exactly what the playbook recommends ✓ |
| Auth | Auth0 (FBA-24 — complete) | Managed provider — exactly what the playbook recommends ✓ |
| Hosting | Not yet deployed | Gap — tracked as FBA-25 |

**Note:** The playbook recommends starting from what you already know best. Spring Boot + Angular is a professional stack that maps to real job skills.

**One deviation worth noting:** This is a full separate frontend + backend (two processes, two codebases). The playbook's worked example uses Next.js API routes for a single codebase. For learning Spring Boot and Angular specifically, the separation is intentional and valuable — but it adds deployment complexity (two things to host instead of one).

### Estimating

The playbook's heuristics:
- Medium feature (like "add a CRUD page"): 1–2 evenings
- Big feature (like "build a real-time sync"): a full weekend
- Whole MVP: 3–5 weekends if scope is right

11 tickets at roughly 1–2 evenings each = approximately 3–5 weekends of work. The scope was calibrated well.

---

## 4. Designing the System

### Design for One User First

The playbook's rule: design for you, alone, on your laptop.

**This project followed this exactly:**
- No authentication layer
- No multi-tenancy in the schema (no `user_id` on any table)
- Single PostgreSQL instance (Docker, local)
- No queues, no cache, no horizontal scaling
- No microservices

The architecture is as simple as possible for the problem. Well done.

### The Default Personal Project Stack

Playbook recommended: `[Browser] → [Single Server] → [Postgres]`

**This project's architecture:**

```
[Browser] ──HTTP──> [Angular (ng serve, port 4200)]
                         |
                    proxy /api →
                         |
                    [Spring Boot (port 8080)]
                         |
                    [PostgreSQL (Docker, port 5432)]
```

Matches the playbook's recommended shape. The proxy config in `angular.json` bridges the two local processes cleanly.

### Picking a Database

Playbook recommendation: **use Postgres. Always.**

**This project:** PostgreSQL 16 via Docker. Managed with Flyway migrations (V1–V7). Schema is clean: UUIDs, `NUMERIC(15,2)` for money, `DATE_TRUNC` check constraint on month columns. Fully aligned.

### Authentication

Playbook recommendation: use a managed auth provider. Don't roll your own.

**Complete (FBA-24):** Auth0 via `spring-boot-starter-oauth2-resource-server` on the backend and `@auth0/auth0-angular` on the frontend. Auth0 handles password hashing, session management, and token rotation for free. The Spring Boot API validates JWTs; the Angular shell redirects unauthenticated users to the Auth0 login page and attaches the bearer token to every `/api` request via an HTTP interceptor.

This remains a single-user app — no `user_id` schema changes needed. Auth locks the API behind a login screen before the app is deployed publicly.

### Hosting and Deployment

**This is the main open gap.** The project has no deployment setup. According to the playbook's milestone model, the project isn't "shipped" until:

> "It's deployed somewhere I can hit it from my phone."

**Recommended path (fastest):**
- Backend: Render or Fly.io (Spring Boot JAR runs on any JVM container)
- Database: Render Postgres free tier, or Supabase
- Frontend: Vercel or Netlify (Angular builds to static files)

The separate frontend + backend adds a deployment step compared to a monolith, but both are straightforward with a PaaS.

### The Scaling Question

Not relevant yet. Single-user, no traffic. The playbook explicitly says: ignore this until you have the problem. This project is correctly ignoring it.

---

## 5. Trade-Offs You'll Actually Make

### Build vs Buy

| Feature | Decision | Playbook Aligned? |
|---------|----------|-------------------|
| Auth | Auth0 managed provider (FBA-24) | Yes — exactly the playbook's recommendation ✓ |
| UI components | Angular Material (buy) | Yes ✓ |
| DB migrations | Flyway (buy) | Yes ✓ |
| ORM | Spring Data JPA / Hibernate (buy) | Yes ✓ |
| Charts | No external lib — Angular Material only | Yes ✓ (kept it simple) |
| Email / notifications | Not built, not needed | Yes ✓ |

No tar pits entered. Everything non-core was sourced from a library or skipped.

### Custom vs Boilerplate

Started from scratch — no boilerplate. The playbook says this is the right call **when the point is learning**. Given the explicit goal of learning Spring Boot + Angular end-to-end, this was correct. You now understand every line.

The cost: more initial setup time (FBA-1, FBA-2). The benefit: deep understanding of the entire stack, no mystery code.

### Polish vs Ship

The project used a Jira + GitHub PR workflow with explicit acceptance criteria per ticket. This is a strong forcing function against over-polishing:

- Each ticket has a clear definition of done
- PRs are reviewed before merging
- The `planning/PLAN.md` documents explicit scope cuts per ticket

The `Polish vs Ship` section of the playbook says: **if a feature isn't core to the value of the app and can be added later, defer it.** Looking at the PLAN.md, the project correctly deferred: animations, empty states, onboarding, dark mode, mobile app, import/export.

**One observation:** The decision to add test coverage (unit + integration tests for all backend services) was a deliberate investment in quality over speed. The playbook would call this a learning-vs-shipping trade-off — and it's a valid one, since the test infrastructure will pay off when changing business logic (budget calculations especially).

### Learning vs Shipping

This project is clearly **learning-first**, which is the right choice given the goal of building Java/Spring Boot + Angular skills from scratch. Signals:

- Built from scratch, not from a boilerplate
- Full test coverage (not typical for "ship fast" projects)
- Jira + PR workflow mirrors a professional team setup
- Flyway migrations, Spring Data JPA, proper DTOs — all real-world patterns

The learning goal is well served. The remaining trade-off is deployment — a shipping goal would have had a deployed version much earlier in the milestone sequence.

---

## 6. Worked Example Comparison

The playbook's worked example is a habit tracker. Mapping the key steps:

| Step | Habit Tracker Example | Finance Budget App |
|------|-----------------------|-------------------|
| Pick the project | Daily habit tracking | Personal finance / YNAB-style budgeting |
| One sentence | "A simple daily habit tracker with streaks" | "A zero-based budget tracker for personal finance" |
| MVP features | 6 bullet points | 6 core loops across 11 tickets |
| Architecture | Next.js + Supabase + Clerk | Spring Boot + Angular + Postgres |
| Walking skeleton | Empty Next.js connected to Supabase | FBA-1 + FBA-7 health check |
| Ship definition | Used for a week without breaking | Not yet declared shipped (no deployment) |

The main structural difference: the habit tracker example has a single codebase (Next.js full-stack) and deploys as one thing. This project has a separate frontend and backend, which adds deployment complexity but provides more separation of concerns and a stronger learning signal for each layer.

---

## 7. Telling People About It

**Current state:** Private GitHub repo with clean PR history. Not publicly shared yet.

**Playbook suggestions worth acting on:**
- The PR descriptions and commit messages already document trade-off decisions well — these can be extracted into a write-up easily.
- A blog post or README write-up on the budget calculation logic (zero-based budgeting, `readyToAssign`, allocation upserts) would be genuinely interesting content and resume-ready.
- The GitHub README could be expanded to describe the architecture, key decisions (single-user, no auth, Flyway migrations, Angular standalone components), and what you learned.

---

## 8. Maintenance (or Not)

**Current state:** Active development just completed. All 11 tickets merged.

**Playbook recommendation:** Maintain it for as long as you're personally using it.

**Recommended maintenance routine once deployed:**
- Monthly: pull dependency updates, run `./mvnw test`, deploy a fresh build, click through the app
- Keep Flyway migrations additive — never edit past migrations
- Set up a daily `pg_dump` to a local backup location (the one non-functional requirement the playbook says actually matters)

**The retirement path:** If you stop using it, archive the repo and take down the deployment. No shame in that — the project already delivered what it needed to deliver: a full-stack Spring Boot + Angular application built end-to-end with a professional workflow.

---

## Summary: Where the Project Stands Against the Playbook

| Playbook Criterion | Status |
|--------------------|--------|
| Idea from real annoyance | ✓ |
| One-sentence description | ✓ |
| MVP scope defined and cut | ✓ |
| Walking skeleton built first | ✓ |
| Boring tech stack | ✓ |
| Postgres for database | ✓ |
| Designed for one user | ✓ |
| Milestone-based plan | ✓ |
| All MVP milestones complete | 13/14 — FBA-25 (deploy) remaining |
| Category management UI | ✓ — FBA-23 done |
| Managed auth (Auth0) | ✓ — FBA-24 done |
| Build vs buy — used libraries | ✓ |
| Database backup configured | **Part of FBA-25** |
| Deployed to production | **Gap — FBA-25** |
| Publicly shared / written up | **Optional — not done yet** |

**Next steps per the playbook:**
1. FBA-25 — deploy backend to Render, frontend to Vercel, set up DB backup — that's the final step between here and "shipped"
