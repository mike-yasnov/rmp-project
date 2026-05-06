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

Load tester имитирует клиентов, создаёт пользователей и случайно выполняет запросы котировок, портфеля и сделок.

Короткая проверка:

```bash
docker compose run --rm -e BOT_COUNT=5 -e DURATION_SEC=5 load-tester
```

Параметры для демонстрации:

```bash
BOT_COUNT=10000 DURATION_SEC=300 docker compose --profile load up load-tester
```

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
- `docker compose build` для `api-gateway`, `core-banking`, `go-ingestion`, `load-tester`;
- `docker compose up -d` для backend;
- smoke-сценарий регистрации, пополнения, покупки и портфеля;
- короткий load-test с нулём ошибок;
- `npm run typecheck` и `npm audit --omit=dev` для React Native.
- `./gradlew :app:assembleDebug` для Native Android в Docker-образе `ghcr.io/cirruslabs/android-sdk:35`.
