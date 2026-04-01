# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Full-stack Spring Boot application for personal finance and budget tracking.

## Stack

- **Backend**: Java / Spring Boot, built with Maven
- **Frontend**: Angular (with Angular Material, SCSS), built with Angular CLI

## Commands

```bash
# Backend build
./mvnw clean package

# Backend run
./mvnw spring-boot:run

# Backend test
./mvnw test

# Single backend test
./mvnw test -Dtest=MyTestClass#myMethod

# Frontend install
cd frontend && npm install

# Frontend serve (proxies /api to localhost:8080)
cd frontend && npx ng serve

# Frontend build
cd frontend && npx ng build

# Accessing Postgres Docker Instance
docker exec -it budget-postgres psql -U budget
```

## Architecture

Architecture notes will be added here as the project takes shape.
