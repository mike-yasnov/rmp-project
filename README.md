# HighLoad Invest Ecosystem

Учебный проект курса **РМП — Разработка мобильных приложений** (ИТМО, весна 2026).
Имитация биржевой платформы: генерация котировок на уровне ядра Linux, потоковая загрузка в аналитическое хранилище и доступ к торгам через мобильные клиенты.

ТЗ: [docs/srs.md](docs/srs.md) · Методичка курса: [docs/РМП_2026_весна_Лекция_1_Практическое_задание.pdf](docs/РМП_2026_весна_Лекция_1_Практическое_задание.pdf)

## Состав монорепы

| Подсистема | Стек | Команда | Статус |
|------------|------|---------|--------|
| [`backend/`](backend/) | Kotlin/Ktor, Go, PostgreSQL, ClickHouse, Redis, OpenTelemetry | мы | рабочий compose-стенд |
| [`mobile-native/`](mobile-native/) | Kotlin + Jetpack Compose | мы | MVP |
| [`mobile-react-native/`](mobile-react-native/) | React Native + Expo | мы | MVP |
| [`module_for_generate_quotes/`](module_for_generate_quotes/) | C, Linux kernel module | группа | готовый драйвер |

## Структура

```
rmp-project/
├── README.md                  # этот файл
├── docs/                      # общая документация курса
│   ├── srs.md                 # ТЗ (общее для всех подсистем)
│   └── РМП_*.pdf              # методичка
├── mobile-native/             # Android Native: Kotlin + Jetpack Compose
├── mobile-react-native/       # React Native / Expo клиент
├── module_for_generate_quotes/ # C Linux kernel module и userspace tools
└── backend/                   # серверная часть
    ├── README.md
    ├── api-gateway/           # Ktor — REST + WebSocket + proxy к Core Banking
    ├── core-banking/          # Ktor — сделки, балансы, портфели
    ├── go-ingestion/          # Go — сбор котировок из /dev/quotes или fallback
    ├── quote-generator/       # Kotlin — legacy генератор
    ├── load-tester/           # Kotlin — имитатор клиентов
    ├── clickhouse/, otel/, nginx/, bruno/
    ├── docker-compose.yml
    └── docs/architecture.md
```

## Quickstart

```bash
cd backend
cp .env.example .env
docker compose up -d --build
```

Подробнее — [backend/README.md](backend/README.md).

Native Android: откройте [mobile-native/](mobile-native/) в Android Studio и запустите `app`.

React Native:
```bash
cd mobile-react-native
npm install
npm run android
```

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
- Описание реализации: [docs/implementation.md](docs/implementation.md)
- Система тестирования: [docs/testing.md](docs/testing.md)
- Сравнение мобильных клиентов: [docs/mobile-comparison.md](docs/mobile-comparison.md)
- API-контракты (Bruno): [backend/bruno/](backend/bruno/)
