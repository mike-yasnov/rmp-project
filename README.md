# HighLoad Invest Ecosystem

Учебный проект курса **РМП — Разработка мобильных приложений** (ИТМО, весна 2026).
Имитация биржевой платформы: генерация котировок на уровне ядра Linux, потоковая загрузка в аналитическое хранилище и доступ к торгам через мобильные клиенты.

ТЗ: [docs/srs.md](docs/srs.md) · Методичка курса: [docs/РМП_2026_весна_Лекция_1_Практическое_задание.pdf](docs/РМП_2026_весна_Лекция_1_Практическое_задание.pdf)

## Состав монорепы

| Подсистема | Стек | Команда | Статус |
|------------|------|---------|--------|
| [`backend/`](backend/) | Kotlin/Ktor, Go, C, PostgreSQL, ClickHouse, Redis, OpenTelemetry | мы | в разработке |
| `mobile/` (Android Native) | Kotlin + Jetpack Compose | — | placeholder |
| `mobile-cross/` (Cross-platform) | React Native | — | placeholder |
| `driver/` | C, Linux kernel module | — | placeholder |

Папки от других команд появятся в репозитории по мере готовности.

## Структура

```
rmp-project/
├── README.md                  # этот файл
├── docs/                      # общая документация курса
│   ├── srs.md                 # ТЗ (общее для всех подсистем)
│   └── РМП_*.pdf              # методичка
└── backend/                   # серверная часть
    ├── README.md              # инструкция по бэкенду
    ├── api-gateway/           # Ktor — REST + WebSocket для клиентов
    ├── core-banking/          # Ktor — сделки, балансы, портфели
    ├── quote-generator/       # Kotlin — заглушка под Go Ingestion
    ├── load-tester/           # Kotlin — имитатор клиентов
    ├── clickhouse/, otel/, nginx/, bruno/
    ├── docker-compose.yml
    └── docs/architecture.md   # архитектура backend-части
```

## Quickstart (backend)

```bash
cd backend
cp .env.example .env
docker compose up -d                # PostgreSQL + ClickHouse + Redis + Jaeger + Prometheus + Grafana
(cd api-gateway   && ./gradlew run)
(cd core-banking  && ./gradlew run)
```

Подробнее — [backend/README.md](backend/README.md).

## Staging

Бэкенд развёрнут на учебном сервере: **http://2.26.49.82/**

Примеры:
- `GET /api/quotes` — список котировок
- `GET /api/portfolio/{userId}` — портфель
- `WS /ws/quotes` — realtime-обновления цены
- `/jaeger/`, `/grafana/` — наблюдаемость

## Документация

- ТЗ: [docs/srs.md](docs/srs.md)
- Архитектура backend: [backend/docs/architecture.md](backend/docs/architecture.md)
- API-контракты (Bruno): [backend/bruno/](backend/bruno/)
