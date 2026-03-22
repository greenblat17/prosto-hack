# Prosto Analytics

AI-powered pivot table builder. Upload CSV/XLSX data or connect to an external PostgreSQL database, build pivot tables via drag-and-drop or natural language, and get instant visualizations with SQL previews.

## Tech Stack

| Layer | Technologies |
|-------|-------------|
| Frontend | React 19, TypeScript, Vite, MobX State Tree, Tailwind CSS v4, dnd-kit, Recharts |
| Backend | Spring Boot 4.0.4, Java 21, PostgreSQL 16, Flyway, JWT, Virtual Threads |
| AI | OpenRouter API (Claude Sonnet) |
| Cache | DuckDB (OLAP cache for external connections), Caffeine (in-memory) |
| Infra | Docker Compose, Nginx, GitHub Actions CI/CD |

## Features

- **Drag-and-drop pivot builder** — rows, columns, values, filters with configurable aggregations
- **AI assistant** — natural language queries generate pivot configurations automatically
- **AI table analysis** — server-side statistical analysis with smart data summarization
- **External database connections** — connect to remote PostgreSQL, browse schemas/tables, build pivots
- **DuckDB OLAP cache** — caches external tables locally for sub-second pivot queries
- **Aggregation functions** — COUNT, SUM, AVG, MIN, MAX, MEDIAN, LIST_DISTINCT, INT_SUM, FIRST, LAST, ORIGINAL
- **Filters** — eq, neq, gt, lt, gte, lte, in
- **Charts** — bar, line, pie via Recharts with view toggle
- **Export** — CSV and Excel
- **Virtual scrolling** — handles large result sets (up to 10,000 rows)
- **File upload** — CSV and XLSX up to 1 GB
- **Demo dataset** — 50,000 sales records pre-loaded

## Quick Start

### Docker Compose (recommended)

```bash
cd infra
docker compose up -d
```

This starts four services:

| Service | URL | Description |
|---------|-----|-------------|
| frontend | http://localhost:3000 | React app (Nginx) |
| backend | http://localhost:8080 | Spring Boot API |
| postgres | localhost:15432 | Main database |
| user-postgres | localhost:25432 | Demo external database |

Swagger UI: http://localhost:8080/swagger-ui.html

### Local Development

**Prerequisites:** Java 21, Node.js 22, PostgreSQL 16

Start only the databases:

```bash
cd infra
docker compose up -d postgres user-postgres
```

Backend:

```bash
cd backend
./gradlew bootRun
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Frontend dev server runs at http://localhost:5173 and calls the backend at http://localhost:8080 directly.

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌────────────────┐
│   Frontend   │────>│   Backend    │────>│  PostgreSQL 16  │
│  React SPA   │<────│ Spring Boot  │<────│  (main DB)      │
│  :3000/:5173 │     │    :8080     │     │   :15432        │
└─────────────┘     └──────┬───────┘     └────────────────┘
                           │
                    ┌──────┴───────┐
                    │              │
              ┌─────▼─────┐  ┌────▼──────────┐
              │ OpenRouter │  │ DuckDB Cache   │
              │  (AI API)  │  │ (OLAP, local)  │
              └───────────┘  └───┬────────────┘
                                 │
                           ┌─────▼──────────┐
                           │ External PG DB  │
                           │   :25432        │
                           └────────────────┘
```

### Backend Packages

```
com.prosto.analytics
├── controller/    # REST endpoints (Auth, Dataset, Pivot, Chat, Export, Connection)
├── service/       # Business logic, SQL builder, AI client, DuckDB cache
├── repository/    # Spring Data JPA repositories
├── model/         # JPA entities (Dataset, User, AggregationType, FilterOperator)
├── dto/           # Request/response DTOs
└── config/        # Security (JWT), CORS, AI, OpenAPI
```

### Frontend Structure

```
src/
├── components/
│   ├── Auth/          # Login/register
│   ├── Layout/        # AppLayout, header, status bar
│   ├── PivotBuilder/  # Drag-and-drop zones, aggregation/filter pickers
│   ├── PivotTable/    # Table renderer, chart view, virtual scrolling
│   ├── FieldPanel/    # Dataset fields sidebar
│   ├── Chat/          # AI chat panel
│   ├── Database/      # External DB browser, connection modal
│   ├── Export/        # CSV/Excel export menu
│   └── ui/            # shadcn/ui primitives
├── stores/            # MobX State Tree (Root, Pivot, Dataset, Result, Chat, Auth, Connection)
├── services/api/      # HTTP client with JWT injection, typed API calls
└── types/             # TypeScript type definitions
```

## API Endpoints

### Authentication (public)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register (email + password) |
| POST | `/api/auth/login` | Login, returns JWT |

### Datasets

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/datasets/upload` | Upload CSV/XLSX (multipart) |
| GET | `/api/datasets` | List user datasets |
| GET | `/api/datasets/{id}/fields` | Get dataset fields with types |
| GET | `/api/datasets/{id}/fields/{name}/stats` | Column statistics |
| DELETE | `/api/datasets/{id}` | Delete dataset |

### Pivot Tables

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/pivot/execute` | Execute pivot query |
| POST | `/api/pivot/external/execute` | Pivot on external DB |

### AI Chat

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/chat/message` | Send message (dataset context) |
| POST | `/api/chat/external/message` | Send message (external DB context) |
| POST | `/api/chat/explain` | AI table analysis |
| GET | `/api/chat/sessions` | List chat sessions |
| POST | `/api/chat/sessions` | Create session |
| DELETE | `/api/chat/sessions/{id}` | Delete session |
| GET | `/api/chat/sessions/{id}/messages` | Session history |

### Export

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/export/csv` | Export pivot result as CSV |
| POST | `/api/export/excel` | Export pivot result as Excel |

### External Connections

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/connections/test` | Test connection |
| POST | `/api/connections` | Connect |
| DELETE | `/api/connections/{id}` | Disconnect |
| GET | `/api/connections/{id}/schemas` | List schemas |
| GET | `/api/connections/{id}/schemas/{s}/tables` | List tables |
| GET | `/api/connections/{id}/schemas/{s}/tables/{t}/fields` | Table fields |

## Environment Variables

### Backend

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `analytics` | Database name |
| `DB_USER` | `analytics` | Database user |
| `DB_PASSWORD` | `analytics` | Database password |
| `JWT_SECRET` | dev default | JWT signing key (256+ bits) |
| `AI_API_KEY` | — | OpenRouter API key (**required** for AI features) |
| `AI_API_URL` | `https://openrouter.ai/api/v1/chat/completions` | AI endpoint |
| `AI_MODEL` | `anthropic/claude-sonnet-4` | AI model identifier |
| `AI_HTTP_REFERER` | `http://localhost:3000` | OpenRouter attribution |
| `AI_APP_TITLE` | `Prosto Analytics` | OpenRouter attribution |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Allowed CORS origins |
| `DUCKDB_PATH` | `/data/duckdb-cache.duckdb` | DuckDB cache file path |
| `DUCKDB_MAX_DISK_GB` | `10` | Max DuckDB cache size |
| `DUCKDB_TTL_MINUTES` | `120` | Cache TTL |

### Frontend

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_URL` | (empty = same origin) | Backend API base URL |

## Database

PostgreSQL 16 with four Flyway migrations:

| Version | Description |
|---------|-------------|
| V1 | Core schema: `datasets`, `dataset_columns` |
| V2 | Demo sales dataset (50,000 rows) |
| V3 | Users table (JWT auth) |
| V4 | Chat sessions and messages |

Hibernate is set to `validate` — all schema changes go through Flyway migrations.

The demo external database (`user-postgres`) ships with three schemas: `sales`, `hr`, `logistics` — pre-populated with sample data.

## Build Commands

### Frontend

```bash
cd frontend
npm install          # install dependencies
npm run dev          # dev server at localhost:5173
npm run build        # typecheck + production build
npm run lint         # ESLint
```

### Backend

```bash
cd backend
./gradlew bootRun                          # run locally
./gradlew build                            # build + test
./gradlew test                             # run tests
./gradlew test --tests "*.SomeTest"        # single test class
./gradlew bootJar                          # build fat JAR
```

### Infrastructure

```bash
cd infra
docker compose up -d                       # start all services
docker compose up -d postgres user-postgres # databases only
docker compose up -d --build               # rebuild and start
docker compose logs -f backend             # tail backend logs
```

## CI/CD

GitHub Actions with two workflows:

**CI** (`ci.yml`) — runs on push/PR to `main`:
- Frontend: Node 22, `npm ci && npm run build`
- Backend: Java 21, `./gradlew build` (includes tests)

**Deploy** (`deploy.yml`) — runs on push to `main` or manual trigger:
- Deploys to VPS via SSH
- Clones repo, writes `.env`, runs `docker compose up -d --build`
- Health check on `/actuator/health`

Required GitHub Secrets: `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `AI_API_KEY`

## License

MIT
