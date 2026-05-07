# APK сборки мобильных клиентов

| Файл | Размер | Стек | applicationId | Тип |
|---|---|---|---|---|
| `highload-invest-native-debug.apk` | ~17 MB | Kotlin + Jetpack Compose | `com.highloadinvest.nativeapp` | debug |
| `highload-invest-react-native-debug.apk` | ~50 MB | React Native + Expo | `com.highloadinvest.reactnative` | debug |
| `highload-invest-react-native-release.apk` | ~58 MB | React Native + Expo | `com.highloadinvest.reactnative` | release |

Debug-сборки подписаны Android debug keystore — годятся для эмулятора и тестирования. Release — оптимизированная и минифицированная, годится для распространения.

Все три можно держать на устройстве одновременно: native и RN имеют разные `applicationId`; debug и release RN перетирают друг друга — установлен будет последний.

## Установка

```bash
adb install dist/apk/highload-invest-native-debug.apk
adb install dist/apk/highload-invest-react-native-release.apk
```

## Подключение к backend

> **Важно:** APK вшиты на `API_BASE_URL = http://10.0.2.2:8080`.
>
> `10.0.2.2` — это специальный адрес Android-эмулятора, указывающий на хост-машину. Поэтому APK работают **только в эмуляторе**, на физическом устройстве — нет (нужна пересборка с другим URL, см. ниже).

Чтобы эмулятор увидел backend на `185.182.108.214`, на хост-машине нужно поднять SSH-туннель и держать его открытым, пока пользуетесь приложением.

### Шаг 1. SSH-туннель (на хост-машине)

```bash
ssh -L 8080:localhost:8080 -N root@185.182.108.214
```

Что произойдёт:
1. Эмулятор делает запрос на `http://10.0.2.2:8080/api/...`
2. `10.0.2.2:8080` → `localhost:8080` хост-машины
3. SSH-туннель пересылает `localhost:8080` → `185.182.108.214:8080`
4. API Gateway отвечает обратно по тому же пути

Флаг `-N` означает «не открывать shell, только туннель». Завершить — `Ctrl+C`.

Если порт `8080` занят локально (например, поднят свой backend), возьмите свободный, например `18080`, и пересоберите APK с этим портом — либо просто остановите локальный сервис.

### Шаг 2. Проверка

```bash
curl http://localhost:8080/api/quotes | head
```

JSON со списком тикеров → туннель работает, эмулятор увидит backend.

### Шаг 3. Запуск приложения

Откройте в эмуляторе одно из установленных приложений и зарегистрируйтесь.

## Альтернативы

### Без туннеля — пересобрать APK с публичным URL

Backend также доступен через nginx на `http://185.182.108.214/` (порт 80) — без SSH:

**Native:**
```kotlin
// mobile-native/app/build.gradle.kts
buildConfigField("String", "API_BASE_URL", "\"http://185.182.108.214\"")
```
```bash
cd mobile-native && ./gradlew :app:assembleDebug
```

**React Native:**
```bash
cd mobile-react-native
EXPO_PUBLIC_API_BASE_URL=http://185.182.108.214 npm run android
```

### Физическое устройство по USB

`10.0.2.2` на физическом устройстве не существует — нужна сборка с публичным URL (см. выше). Альтернатива через `adb reverse` обычно не выручает: `10.0.2.2` всё равно не резолвится на устройстве.

## Какие порты у backend

| Порт | Что | Доступ |
|---|---|---|
| `185.182.108.214:80` | nginx → api-gateway | публичный |
| `185.182.108.214:8080` | api-gateway напрямую | публичный |
| `185.182.108.214:8081` | core-banking напрямую | публичный |
| `185.182.108.214/jaeger/` | Jaeger UI | публичный |
| `185.182.108.214/grafana/` | Grafana | публичный |

Все REST/WebSocket пути идентичные на 80 и 8080 — выбирайте любой.
