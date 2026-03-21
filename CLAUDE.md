# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Prosto Analytics — AI-powered pivot table builder. Users upload CSV/XLSX data, build pivot tables via drag-and-drop or natural language, and get instant visualizations and SQL previews.

## Architecture

Monorepo with three top-level directories:

- **frontend/** — React 19 + TypeScript SPA (Vite, MobX State Tree, Tailwind CSS v4, dnd-kit, Recharts)
- **backend/** — Spring Boot 4.0 (Java 25, Gradle 9.4, PostgreSQL, Flyway, JWT auth, OpenAPI/Swagger)
- **infra/** — Docker Compose (Postgres 16 on port 15432, backend on port 8080)

### Backend (Spring Boot)

Package: `com.prosto.analytics` — standard layered architecture:
- `controller/` — REST endpoints (Auth, Dataset, Pivot, Chat, Export)
- `service/` — Business logic (AuthService, DatasetService, PivotService, AiChatService, ExportService, JwtService)
- `repository/` — Spring Data JPA repos
- `model/` — JPA entities (Dataset, User, FieldType, FilterOperator)
- `dto/` — Request/response DTOs
- `config/` — Security (JWT filter), CORS, AI config, OpenAPI

Database migrations in `src/main/resources/db/migration/` (Flyway, V1-V3). Hibernate set to `validate` — all schema changes must go through Flyway.

AI chat uses OpenRouter API (configurable via `AI_API_URL`, `AI_API_KEY`, `AI_MODEL` env vars).

### Frontend (React + MobX State Tree)

Path alias: `@/` maps to `src/`.

Stores (`src/stores/`): RootStore composes PivotStore, DatasetStore, ChatStore, ResultStore, AuthStore.

API layer in `src/services/api/` — `client.ts` is the shared HTTP client with JWT header injection.

UI components use shadcn/ui pattern (`src/components/ui/`) with Base UI primitives.

## Commands

### Frontend
```bash
cd frontend
npm install          # install deps
npm run dev          # dev server at localhost:5173
npm run build        # typecheck + production build
npm run lint         # ESLint
```

### Backend
```bash
cd backend
./gradlew bootRun                    # run locally (needs Postgres)
./gradlew build                      # build + test
./gradlew test                       # run tests
./gradlew test --tests "*.SomeTest"  # single test class
./gradlew bootJar                    # build fat JAR
```

### Infrastructure
```bash
cd infra
docker compose up -d          # start Postgres + backend
docker compose up -d postgres # Postgres only (for local backend dev)
```

Postgres exposed on **port 15432** (not default 5432). Connection: `analytics/analytics@localhost:15432/analytics`.

## Key Details

- Frontend proxies nothing — it calls backend directly at `localhost:8080`
- JWT auth: tokens stored client-side, attached via Authorization header in `client.ts`
- Swagger UI available at `http://localhost:8080/swagger-ui.html`
- Max file upload size: 1GB
- Pivot results capped at 10,000 rows (`app.pivot.max-result-rows`)
