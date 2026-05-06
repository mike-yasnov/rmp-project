# Описание системы тестирования

## Smoke-тест backend

Стенд запускается одной командой:

```bash
cd backend
docker compose up -d --build
```

Проверки доступности:

```bash
curl -fsS http://localhost:8080/health
curl -fsS http://localhost:8081/health
curl -fsS http://localhost:8080/api/quotes
```

Проверка пользовательского сценария:

```bash
USER_JSON=$(curl -fsS -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","email":"demo@example.test","initialBalance":1000000}')

USER_ID=$(printf '%s' "$USER_JSON" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

curl -fsS -X POST "http://localhost:8080/api/accounts/$USER_ID/deposit" \
  -H 'Content-Type: application/json' \
  -d '{"amount":50000}'

curl -fsS -X POST http://localhost:8080/api/trades \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",\"ticker\":\"SBER\",\"action\":\"BUY\",\"lots\":2,\"pricePerLot\":250.0}"

curl -fsS "http://localhost:8080/api/portfolio/$USER_ID"
```

Ожидаемый результат: баланс уменьшается на сумму покупки, в портфеле появляется позиция `SBER` с двумя лотами.

## Нагрузочный тест

Load tester имитирует клиентов через API Gateway, создаёт пользователей, выполняет запросы котировок, портфеля и сделок. Для больших прогонов `BOT_COUNT` означает количество логических клиентов, а `ACTIVE_REQUESTS` ограничивает число одновременно активных REST-запросов.

Короткая проверка:

```bash
docker compose -f docker-compose.yml -f otel/docker-compose.otel.yml -f docker-compose.driver.yml run --rm \
  -e BOT_COUNT=50 \
  -e DURATION_SEC=10 \
  -e CREATE_PARALLELISM=20 \
  -e ACTIVE_REQUESTS=20 \
  -e WS_PERCENT=20 \
  load-tester
```

Проверка 10k логических клиентов:

```bash
docker compose -f docker-compose.yml -f otel/docker-compose.otel.yml -f docker-compose.driver.yml run --rm \
  -e BOT_COUNT=10000 \
  -e DURATION_SEC=15 \
  -e CREATE_PARALLELISM=100 \
  -e ACTIVE_REQUESTS=50 \
  -e WS_PERCENT=0 \
  -e REQUEST_DELAY_MIN_MS=100 \
  -e REQUEST_DELAY_MAX_MS=300 \
  load-tester
```

## Проверка kernel module через Docker Compose

Driver-режим загружает Linux kernel module в ядро хоста, поэтому проверяется только на native Linux с установленными headers текущего ядра:

```bash
cd backend
sudo apt install "linux-headers-$(uname -r)"
docker compose -f docker-compose.yml -f docker-compose.driver.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.driver.yml ps
head -5 /dev/quotes
cat /proc/quotes_stat
curl 'http://localhost:8123/?query=SELECT%20count()%20FROM%20quotes'
```

Ожидаемый результат: контейнер `highload-quote-driver` healthy, `/dev/quotes` отдаёт строки котировок, счётчик `total_written` в `/proc/quotes_stat` растёт, ClickHouse получает новые строки.

## Проверка Observability

Запуск:

```bash
cd backend
docker compose -f docker-compose.yml -f otel/docker-compose.otel.yml -f docker-compose.driver.yml up -d --build
```

Проверки:

```bash
curl -fsS 'http://localhost:16686/api/services'
curl -fsS 'http://localhost:9090/api/v1/query?query=quotes_received_total'
curl -fsS 'http://localhost:9090/api/v1/query?query=quotes_inserted_total'
```

Ожидаемый результат: Jaeger показывает сервисы `api-gateway`, `core-banking`, `go-ingestion`; Prometheus видит метрики ingestion.

## Проверки React Native

```bash
cd mobile-react-native
npm install
npm run typecheck
npm audit --omit=dev
```

## Проверки Native Android

Проект открывается в Android Studio как Gradle/Android проект. Проверка конфигурации Gradle:

```bash
cd mobile-native
./gradlew tasks
```

Для сборки APK требуется установленный Android SDK или Android Studio. На машине без SDK `compileDebugKotlin` ожидаемо падает с сообщением `SDK location not found`.

В Docker-образе с Android SDK debug APK проверяется так:

```bash
docker run --rm -v "$PWD":/workspace -w /workspace \
  ghcr.io/cirruslabs/android-sdk:35 \
  ./gradlew --no-daemon :app:assembleDebug
```

## Проверенный результат

На текущем стенде были выполнены:

- `go test ./...` для `backend/go-ingestion`;
- `docker compose config --quiet`;
- `docker compose -f docker-compose.yml -f otel/docker-compose.otel.yml -f docker-compose.driver.yml config --quiet`;
- `docker compose build` для `api-gateway`, `core-banking`, `go-ingestion`, `load-tester`;
- `docker compose up -d` для backend;
- smoke-сценарий регистрации, пополнения, покупки и портфеля;
- проверка драйвера через compose: `/dev/quotes`, `/proc/quotes_stat`, рост ClickHouse и API `/api/quotes`;
- WebSocket realtime-проверка: получение сообщений из `/ws/quotes`;
- короткий load-test REST+WebSocket: 701 успешный запрос, 0 ошибок, 220 WebSocket сообщений;
- load-test 10k логических клиентов REST: 3469 успешных запросов, 0 ошибок;
- Observability: Jaeger services `go-ingestion`, `api-gateway`, `core-banking`; Prometheus метрики `quotes_received_total`, `quotes_inserted_total`;
- `npm run typecheck` и `npm audit --omit=dev` для React Native.
- `./gradlew :app:assembleDebug` для Native Android в Docker-образе `ghcr.io/cirruslabs/android-sdk:35`.
- kernel module компилировался как `quotes_driver.ko`; runtime-загрузка проверяется командой compose override выше.
