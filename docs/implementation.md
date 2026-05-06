# Описание реализации

## Состав системы

Проект реализован как учебная экосистема брокера:

- `backend/api-gateway` — Kotlin/Ktor сервис для мобильных клиентов: REST, WebSocket, чтение котировок из ClickHouse и прокси операций в Core Banking.
- `backend/core-banking` — Kotlin/Ktor сервис пользовательских данных: пользователи, счета, пополнение, сделки, портфель.
- `backend/go-ingestion` — Go сервис сбора котировок: читает `/dev/quotes`, пишет батчи в ClickHouse и публикует обновления в Redis.
- `module_for_generate_quotes` — Linux kernel module на C, генерирующий котировки как character device `/dev/quotes`.
- `mobile-native` — Android Native клиент на Kotlin + Jetpack Compose.
- `mobile-react-native` — React Native клиент на Expo.
- `backend/load-tester` — Kotlin-имитатор мобильных клиентов.

## Поток котировок

Драйвер ядра отдаёт текстовый поток котировок через `/dev/quotes`. Go ingestion читает устройство, нормализует запись до JSON-модели `ticker/price/volume/timestamp`, накапливает батчи и вставляет их в таблицу ClickHouse `quotes`. После каждой котировки ingestion публикует сообщение в Redis Pub/Sub канал `quotes:updates`.

Если `/dev/quotes` недоступен внутри Docker, ingestion включает fallback-генератор. Это сохраняет демонстрационный стенд работоспособным без загрузки kernel module на машине проверяющего.

## Backend API

API Gateway отдаёт клиентам:

- `GET /api/quotes`;
- `GET /api/quotes/{ticker}`;
- `GET /api/quotes/{ticker}/candles`;
- `WS /ws/quotes`;
- `POST /api/users`;
- `POST /api/accounts/{userId}/deposit`;
- `POST /api/trades`;
- `GET /api/trades/{userId}`;
- `GET /api/portfolio/{userId}`.

Котировки читаются из ClickHouse. Торговые операции проксируются в Core Banking. Финансовые данные находятся в PostgreSQL.

## ACID-операции

Покупка и продажа реализованы в `PostgresTradeExecutor`. Операция выполняется в одной транзакции PostgreSQL:

- блокируется строка счёта через `SELECT ... FOR UPDATE`;
- для продажи блокируется позиция портфеля;
- валидируется баланс или количество лотов;
- обновляется баланс;
- обновляется позиция портфеля;
- записывается сделка;
- выполняется `COMMIT` или `ROLLBACK`.

## Клиенты

Оба мобильных клиента реализуют одинаковый пользовательский сценарий:

- регистрация учебного пользователя;
- просмотр текущих котировок;
- получение realtime-обновлений через WebSocket;
- просмотр свечного графика выбранного тикера;
- пополнение счёта учебными деньгами;
- покупка и продажа целого числа лотов;
- просмотр портфеля.

Для Android emulator оба клиента по умолчанию используют `http://10.0.2.2:8080`.
