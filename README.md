# finance-budget-app
Full-stack app to track personal finances and budgets. Spring Boot backend, Angular frontend.

## Stack
- **Backend**: Java 21 / Spring Boot 3.x / Maven
- **Frontend**: Angular / Angular Material / SCSS
- **Database**: PostgreSQL 16

## PostgreSQL via Docker
```bash
docker run -d --name budget-postgres \
  -e POSTGRES_DB=budget -e POSTGRES_USER=budget -e POSTGRES_PASSWORD=budget \
  -p 5432:5432 postgres:16
```

## Running locally

**Backend**
```bash
./mvnw spring-boot:run
```

**Frontend** (proxies `/api` to `localhost:8080`)
```bash
cd frontend && npx ng serve
```