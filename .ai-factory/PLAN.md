# Implementation Plan: Kotlin Backend Setup

Branch: none
Created: 2026-04-09

## Settings
- Testing: yes
- Logging: verbose (SLF4J + Logback, configurable via LOG_LEVEL)

## Обзор

Создание каркаса Kotlin-бэкенда из двух отдельных Gradle-проектов:
1. **api-gateway** — Ktor-приложение: REST + WebSocket, маршрутизация, подключение к ClickHouse/Redis
2. **core-banking** — бизнес-логика: пользователи, балансы, сделки, PostgreSQL

Оба проекта следуют Clean Architecture (domain → application → infrastructure → presentation).
Docker Compose для всей инфраструктуры (PostgreSQL, ClickHouse, Redis).

## Commit Plan
- **Commit 1** (после задач 1-3): "feat: init api-gateway Ktor project with clean architecture"
- **Commit 2** (после задач 4-6): "feat: init core-banking project with PostgreSQL"
- **Commit 3** (после задач 7-9): "feat: add docker-compose, API routes, and tests"

## Tasks

### Phase 1: API Gateway — каркас

- [x] **Task 1: Инициализировать Gradle-проект api-gateway**
  Создать `api-gateway/` с Kotlin/Ktor:
  - `build.gradle.kts` с зависимостями: Ktor server (Netty), kotlinx.serialization, Koin, Logback, ClickHouse JDBC, Jedis (Redis), ktor-websockets, ktor-content-negotiation
  - `gradle.properties`, `settings.gradle.kts`
  - `src/main/resources/application.conf` — конфигурация Ktor (порт, хост)
  - `src/main/resources/logback.xml` — настройка логирования (LOG_LEVEL из env)
  - `src/main/kotlin/Application.kt` — точка входа Ktor

  LOGGING: Logback с конфигурацией через `LOG_LEVEL` env var. Формат: `[timestamp] [level] [logger] - message`.

  Файлы: `api-gateway/build.gradle.kts`, `api-gateway/settings.gradle.kts`, `api-gateway/gradle.properties`, `api-gateway/src/main/kotlin/com/highloadinvest/gateway/Application.kt`, `api-gateway/src/main/resources/application.conf`, `api-gateway/src/main/resources/logback.xml`

- [x] **Task 2: Создать Clean Architecture структуру api-gateway**
  Создать пакеты и базовые интерфейсы:
  - `domain/entities/` — Quote, Ticker (data classes)
  - `domain/repositories/` — QuoteRepository (interface)
  - `application/usecases/` — GetCurrentQuotes, SubscribeToQuotes
  - `infrastructure/clickhouse/` — ClickHouseQuoteRepository
  - `infrastructure/redis/` — RedisQuotePublisher
  - `infrastructure/di/` — Koin modules
  - `presentation/routes/` — QuoteRoutes (Ktor routing)
  - `presentation/dto/` — QuoteResponse
  - `presentation/websocket/` — WebSocketHandler

  LOGGING: Логировать в каждом use case: entry с параметрами, result, errors. В repository: запросы к БД, время выполнения.

  Файлы: все пакеты в `api-gateway/src/main/kotlin/com/highloadinvest/gateway/`

- [x] **Task 3: Настроить Koin DI и запуск Ktor с плагинами**
  - Koin module: подключение репозиториев и use cases
  - Ktor plugins: ContentNegotiation (JSON), WebSockets, StatusPages (error handling), CallLogging
  - Structured error responses (ErrorResponse data class)
  - Health check endpoint: `GET /health`

  LOGGING: CallLogging plugin для HTTP-запросов. StatusPages логирует все ошибки с контекстом.

  Файлы: `Application.kt`, `infrastructure/di/AppModule.kt`, `presentation/plugins/`, `presentation/routes/HealthRoutes.kt`

<!-- 🔄 Commit checkpoint: задачи 1-3 — "feat: init api-gateway Ktor project with clean architecture" -->

### Phase 2: Core Banking — каркас

- [x] **Task 4: Инициализировать Gradle-проект core-banking**
  Создать `core-banking/` с Kotlin/Ktor:
  - `build.gradle.kts` с зависимостями: Ktor server (Netty), kotlinx.serialization, Koin, Logback, PostgreSQL JDBC, HikariCP
  - Конфигурация application.conf (порт отличный от api-gateway)
  - logback.xml с LOG_LEVEL

  LOGGING: Аналогично api-gateway — Logback, configurable, structured.

  Файлы: `core-banking/build.gradle.kts`, `core-banking/settings.gradle.kts`, `core-banking/src/main/resources/application.conf`, `core-banking/src/main/resources/logback.xml`, `core-banking/src/main/kotlin/com/highloadinvest/banking/Application.kt`

- [x] **Task 5: Создать Clean Architecture структуру core-banking**
  - `domain/entities/` — User, Account, Trade, Portfolio (data classes)
  - `domain/repositories/` — UserRepository, AccountRepository, TradeRepository (interfaces)
  - `application/usecases/` — ExecuteTrade, GetPortfolio, GetBalance
  - `infrastructure/postgres/` — PostgresUserRepository, PostgresAccountRepository, PostgresTradeRepository (raw SQL через JDBC + HikariCP)
  - `infrastructure/di/` — Koin modules
  - `presentation/routes/` — TradeRoutes, PortfolioRoutes, UserRoutes
  - `presentation/dto/` — TradeRequest, TradeResponse, PortfolioResponse

  LOGGING: В каждой PostgreSQL-операции: SQL-запрос (DEBUG), время выполнения, row count. В use cases: entry/exit, validation results. В trade execution: полный аудит-лог (user, ticker, lots, price, result).

  Файлы: все пакеты в `core-banking/src/main/kotlin/com/highloadinvest/banking/`

- [x] **Task 6: Реализовать PostgreSQL-подключение и базовую миграцию**
  - HikariCP DataSource конфигурация через application.conf
  - SQL-миграция (простой `.sql` файл): CREATE TABLE users, accounts, trades, portfolio
  - DatabaseFactory object для инициализации при старте
  - Реализация PostgresAccountRepository с raw SQL (SELECT, INSERT, UPDATE в транзакции)

  LOGGING: Логировать подключение к БД (хост, порт, pool size). При ошибках подключения — ERROR с деталями. Каждая транзакция — INFO с длительностью.

  Файлы: `core-banking/src/main/kotlin/.../infrastructure/postgres/DatabaseFactory.kt`, `core-banking/src/main/resources/db/migration/V1__init.sql`, `core-banking/src/main/kotlin/.../infrastructure/postgres/PostgresAccountRepository.kt`

<!-- 🔄 Commit checkpoint: задачи 4-6 — "feat: init core-banking project with PostgreSQL" -->

### Phase 3: Инфраструктура и тесты

- [x] **Task 7: Создать docker-compose.yml для всей инфраструктуры**
  - PostgreSQL 16
  - ClickHouse (latest)
  - Redis 7
  - Volumes для персистентности
  - Сеть для взаимодействия контейнеров
  - `.env.example` с переменными

  НЕ включать сами Kotlin-сервисы в compose (их запускаем локально при разработке).

  LOGGING: Все контейнеры пишут в stdout.

  Файлы: `docker-compose.yml`, `.env.example`

- [x] **Task 8: Добавить REST-эндпоинты в api-gateway**
  - `GET /api/quotes` — текущие котировки (из ClickHouse)
  - `GET /api/quotes/{ticker}` — котировки по тикеру
  - `GET /api/quotes/{ticker}/candles` — свечные данные
  - `WebSocket /ws/quotes` — подписка на обновления (stub, подключим Redis PubSub)
  - kotlinx.serialization для всех DTO

  LOGGING: Каждый запрос — INFO (path, params). Ответ — DEBUG (payload size). Ошибки — ERROR (full context).

  Файлы: `api-gateway/src/main/kotlin/.../presentation/routes/QuoteRoutes.kt`, `api-gateway/src/main/kotlin/.../presentation/dto/*.kt`, `api-gateway/src/main/kotlin/.../presentation/websocket/QuoteWebSocket.kt`

- [x] **Task 9: Написать тесты**
  - api-gateway: Ktor testApplication — тесты для `/health`, `/api/quotes` endpoints
  - core-banking: unit-тесты для ExecuteTrade use case (mock repository)
  - core-banking: тесты для PortfolioRoutes через Ktor testApplication

  LOGGING: В тестах использовать SLF4J test logger.

  Файлы: `api-gateway/src/test/kotlin/.../routes/QuoteRoutesTest.kt`, `core-banking/src/test/kotlin/.../usecases/ExecuteTradeTest.kt`, `core-banking/src/test/kotlin/.../routes/PortfolioRoutesTest.kt`

<!-- 🔄 Commit checkpoint: задачи 7-9 — "feat: add docker-compose, API routes, and tests" -->

### Phase 4: Git

- [x] **Task 10: Инициализировать git-репозиторий и сделать первый коммит**
  - `git init` в корне mobilki/
  - `.gitignore` для Kotlin/Gradle/IDE
  - Проверить что всё собирается: `./gradlew build` в обоих проектах
  - Initial commit

  Файлы: `.gitignore`
