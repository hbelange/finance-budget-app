# Deployment — Platform Decisions & Tradeoffs

Documents the hosting choices for FBA-25 and the reasoning behind them.

---

## Stack Summary

| Layer | Platform | Alternative considered |
|-------|----------|------------------------|
| Frontend | Vercel | Netlify, Cloudflare Pages, GitHub Pages |
| Backend | Render Web Service | Fly.io, Railway, Heroku |
| Database | Render PostgreSQL | Neon, Supabase |

---

## Frontend — Vercel

**Why Vercel:** Zero config for static file hosting. Angular's `ng build` output drops into `dist/` and Vercel picks it up automatically. Instant deploys on push to main, free HTTPS, global CDN, and deploy previews with no extra setup.

**Why not the alternatives:**
- **Netlify** — functionally equivalent and would work fine. Coin flip between the two.
- **Cloudflare Pages** — legitimate alternative with better edge performance, but the dashboard and tooling are less mature.
- **GitHub Pages** — ruled out because it doesn't support SPA client-side routing cleanly. Angular's router needs all paths to serve `index.html`; GitHub Pages requires a `404.html` workaround to achieve this.

**Known tradeoff:** Vercel's free tier has bandwidth and build minute limits. Irrelevant for a single-user personal app.

---

## Backend — Render Web Service

**Why Render:** Supports deploying a Spring Boot JAR or Docker image with minimal config — connect the repo, set environment variables, done. No custom build manifest required.

**Why not the alternatives:**
- **Fly.io** — technically the stronger choice. Faster cold starts, true global deployment, better free tier, no forced spin-down. The cost is a `fly.toml` config file and familiarity with its CLI. Added setup friction for marginal gain on a personal app.
- **Railway** — arguably the easiest of all options. Ruled out due to less predictable pricing on the free tier.
- **Heroku** — was the default answer for this use case for years. Eliminated when it removed its free tier in 2022.

**Known tradeoff:** Render's free tier spins down a web service after 15 minutes of inactivity. The next request triggers a cold start of ~30 seconds. Acceptable for a personal app checked daily; noticeable if the app hasn't been used in a while. Fly.io does not have this limitation.

---

## Database — Render PostgreSQL

**Why Render:** Co-locating the database with the backend on the same platform eliminates cross-provider network latency and means one fewer dashboard to manage.

**Why not the alternatives:**
- **Neon** — strong alternative. Serverless Postgres with branching, generous free tier, and no 90-day expiry. Would be the better technical pick if not for the cross-platform management overhead.
- **Supabase** — bundles Postgres with an auth layer and auto-generated REST API that this project doesn't need. Adds surface area for no benefit given Auth0 is already handling auth.

**Known tradeoff:** Render's free PostgreSQL instance is deleted after 90 days of inactivity. Options to address this before the deadline:
1. Upgrade to Render's paid Postgres plan (~$7/month)
2. Migrate to Neon, which has no expiry on its free tier

---

## Alternative Worth Reconsidering

**Fly.io (backend) + Neon (database)** is the stronger technical combination:
- No cold start spin-down
- No 90-day database expiry
- Both have generous free tiers

The reason this wasn't chosen is operational simplicity: keeping backend and database on one platform means one set of credentials, one dashboard, and one support channel. For a solo project that matters more than the technical edge.

---

## Auth0 Production Setup (part of FBA-25)

When the production frontend URL is known, add it to all three Auth0 application settings:
- Allowed Callback URLs
- Allowed Logout URLs
- Allowed Web Origins

The backend's Auth0 config (`issuer-uri`, `audiences`) is set via Render environment variables — not committed to the repo.
