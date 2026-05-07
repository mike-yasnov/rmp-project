# APK сборки мобильных клиентов

| Файл | Размер | Стек | applicationId | Тип |
|---|---|---|---|---|
| `highload-invest-native-debug.apk` | ~17 MB | Kotlin + Jetpack Compose | `com.highloadinvest.nativeapp` | debug |
| `highload-invest-react-native-debug.apk` | ~50 MB | React Native + Expo | `com.highloadinvest.reactnative` | debug |
| `highload-invest-react-native-release.apk` | ~58 MB | React Native + Expo | `com.highloadinvest.reactnative` | release |

`com.highloadinvest.nativeapp` и `com.highloadinvest.reactnative` имеют разные `applicationId` и могут стоять одновременно. Из RN-вариантов установится только один — последний установленный (debug перетирает release и наоборот).

## Backend

Все APK собраны с прод-URL:

```
API_BASE_URL = http://185.182.108.214:8080
```

Сервер публичный, никаких туннелей не нужно — приложение подключается напрямую и в эмуляторе, и на физическом устройстве. Cleartext (`http://`) разрешён в манифесте обоих клиентов.

Backend доступен и через nginx на порту 80 (`http://185.182.108.214/api/...`) — оба пути отдают одинаковые `/api/*` и `/ws/*`.

## Установка

### В Android-эмуляторе

Эмулятор должен быть запущен (`emulator -avd <name> &` или из Android Studio), затем:

```bash
adb install dist/apk/highload-invest-native-debug.apk
adb install dist/apk/highload-invest-react-native-release.apk
```

После установки приложения появятся в лаунчере. Можно запустить вручную или из терминала:
```bash
adb shell am start -n com.highloadinvest.nativeapp/.MainActivity
adb shell am start -n com.highloadinvest.reactnative/.MainActivity
```

### На физическом устройстве

1. Включить «Отладку по USB» (Developer Options → USB debugging)
2. Подключить кабель → подтвердить fingerprint компа на устройстве
3. Проверить, что устройство видно: `adb devices` (должно показывать строку с `device`)
4. `adb install dist/apk/...` — то же, что и для эмулятора

Wi-Fi устройства и компа могут быть в разных сетях — это не имеет значения, потому что приложение ходит напрямую на публичный сервер `185.182.108.214`, а не через хост-машину.

## Проверка, что backend жив

```bash
curl http://185.182.108.214:8080/api/quotes | head
```

Если в ответе JSON со списком тикеров (AAPL, MSFT, GOOG, …) — сервер работает, приложение подключится. Если timeout/connection refused — сервер лежит, любые APK дадут «Ошибка сети» при открытии экранов.

## Сценарий использования

1. Установить APK
2. Открыть приложение
3. Зарегистрироваться (введите имя пользователя и e-mail) или войти
4. Биржа → выбрать тикер → купить лот / выставить лимит-ордер
5. Счёт → пополнить баланс / посмотреть портфель

## Разработка локально

Если хотите тестировать на собственном backend (поднятый рядом через `cd backend && docker compose up -d`), пересоберите APK с другим URL:

**Native** ([`mobile-native/app/build.gradle.kts`](../../mobile-native/app/build.gradle.kts)):
```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
```
```bash
cd mobile-native && ./gradlew :app:assembleDebug
```

`10.0.2.2` — спецадрес Android-эмулятора, указывающий на `localhost` хост-машины.

**React Native** ([`mobile-react-native/.env`](../../mobile-react-native/.env)):
```
EXPO_PUBLIC_API_BASE_URL=http://10.0.2.2:8080
```
```bash
cd mobile-react-native && npm run android
```

## Какие порты на сервере

| URL | Что | Доступ |
|---|---|---|
| `http://185.182.108.214/api/*` | nginx → api-gateway | публичный |
| `http://185.182.108.214:8080/api/*` | api-gateway напрямую | публичный |
| `ws://185.182.108.214/ws/quotes` | realtime-котировки через nginx | публичный |
| `ws://185.182.108.214:8080/ws/quotes` | realtime-котировки напрямую | публичный |
| `http://185.182.108.214:8081` | core-banking напрямую | публичный |
| `http://185.182.108.214/jaeger/` | Jaeger UI | публичный |
| `http://185.182.108.214/grafana/` | Grafana | публичный |

REST и WebSocket пути идентичны на 80 и 8080.
