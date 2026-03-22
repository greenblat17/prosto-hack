# Оптимизации производительности Prosto Analytics

## Проблема

Система pivot-таблиц подключается к пользовательской PostgreSQL (read-only). Таблицы до 20М+ строк, 200+ колонок. Время отклика было **12-15 секунд**. Цель: **<1 секунды**.

## Результат

| Сценарий | До | После |
|----------|-----|-------|
| Pivot на 20М строк (PostgreSQL напрямую) | 12с | 4с (параллельные запросы) |
| Pivot на 20М строк (DuckDB кэш) | — | <1с |
| Повторный идентичный запрос (Caffeine) | 12с | 0мс |
| Рендеринг 10K строк в таблице | ~500мс | ~10мс |

## Реализованные оптимизации

### Backend

#### 1. DuckDB OLAP-кэш
Embedded columnar database как прозрачный кэш для внешних подключений. При выборе таблицы данные фоново загружаются из пользовательской PostgreSQL в локальный DuckDB. Все последующие pivot-запросы идут в DuckDB — columnar storage агрегирует 20М строк за <1с.

- `DuckDBCacheService.java` — кэш-менеджер с TTL, LRU eviction, thread-safe writes
- `DuckDBDataSource.java` — DataSource обёртка для thread-safe reads через `duplicate()`
- `SqlDialect.java` — абстракция SQL-диалекта (PG vs DuckDB cast syntax)
- Загрузка через `COPY TO STDOUT` + `DuckDBAppender` (быстрее чем row-by-row cursor)
- Кэш привязан к `host:port:db:schema:table` (переживает reconnect)
- TTL 2 часа, configurable через env var

#### 2. Параллельное выполнение запросов
3 SQL-запроса (COUNT, MAIN, TOTALS) выполняются одновременно через `CompletableFuture` вместо последовательно. Время: `sum(3 запроса)` → `max(3 запроса)`.

#### 3. Caffeine кэш результатов
In-memory кэш pivot-результатов. 500 записей, TTL 5 минут. Повторные запросы и пагинация назад — 0мс. Ключ: SHA-256 от `tableName + config + offset + limit`.

#### 4. GZIP compression
Сжатие JSON-ответов >1KB. Pivot payload 1-3 МБ → 100-300 КБ. Одна строка в application.yml.

#### 5. Virtual threads (Java 21)
`spring.threads.virtual.enabled: true`. Эффективнее для I/O-heavy нагрузки при многих одновременных пользователях.

#### 6. Query timeout
`statement_timeout: 30000` в HikariCP. Защита от зависших запросов на больших таблицах.

### Frontend

#### 7. Virtual scrolling
`@tanstack/react-virtual` — рендерятся только ~50 видимых строк вместо 10K. DOM: 10K элементов → 50.

#### 8. Удалена staggered анимация
`animationDelay: rowIdx * 20ms` убрана — на 10K строк последняя строка анимировалась через 200 секунд.

#### 9. Удалён SQL preview round-trip
Убран отдельный HTTP-запрос к `/api/pivot/sql`. Минус 1 round-trip на каждый pivot.

#### 10. AbortController
Реальная отмена HTTP-запросов при смене конфигурации. Кнопка "Отменить" в UI при долгих запросах.

### Инфраструктура

#### 11. Java 21 (downgrade с 25)
Все фичи доступны, но совместимо с DuckDB native libs (glibc). Non-Alpine Docker image (`eclipse-temurin:21-jre`).

#### 12. Docker volume для DuckDB
Persistent volume `/data/duckdb-cache.duckdb` для кэша между рестартами контейнера.

## Архитектура

```
Пользователь → Frontend → Backend → Router:
                                      ├── Caffeine кэш → 0мс (повторный запрос)
                                      ├── DuckDB кэш → <1с (данные загружены)
                                      └── PostgreSQL → ~4с (параллельные запросы)
                                           └── Фоновая загрузка в DuckDB...
```

## Конфигурация

```yaml
app:
  duckdb:
    path: ${DUCKDB_PATH:/data/duckdb-cache.duckdb}
    max-disk-gb: ${DUCKDB_MAX_DISK_GB:10}      # Лимит диска для кэша
    ttl-minutes: ${DUCKDB_TTL_MINUTES:120}      # TTL кэша (2 часа)
```

## Файлы

### Новые
- `backend/.../service/DuckDBCacheService.java`
- `backend/.../service/DuckDBDataSource.java`
- `backend/.../service/SqlDialect.java`

### Модифицированные
- `backend/.../service/PivotService.java` — DuckDB routing, parallel queries, Caffeine
- `backend/.../service/PivotSqlBuilder.java` — SqlDialect support
- `backend/.../service/ConnectionService.java` — getConnectionInfo(), credentials
- `backend/.../controller/ConnectionController.java` — cache trigger, evict on disconnect
- `backend/src/main/resources/application.yml` — DuckDB config, GZIP, virtual threads
- `backend/build.gradle.kts` — DuckDB, Caffeine dependencies
- `backend/Dockerfile` — non-Alpine, /data directory
- `infra/docker-compose.yml` — DuckDB volume, env vars
- `frontend/.../PivotTable.tsx` — virtual scrolling
- `frontend/.../ResultStore.ts` — AbortController, removed SQL preview
- `frontend/.../pivotApi.ts` — removed SQL preview functions
