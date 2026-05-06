# quotes_driver

Linux kernel char device `/dev/quotes` — генератор биржевых котировок.

## Структура проекта

```
quotes_driver/
├── CMakeLists.txt             
├── cmake/
│   └── KernelModule.cmake      # Вспомогательный модуль для сборки .ko
│
├── kernel/                     # Исходники ядра
    ├── CMakeLists.txt
│   ├── Makefile                # Kbuild-файл (make -C kernel/)
│   ├── quotes_driver.c         # Char device / ring buffer / kthread / hrtimer
│   └── quotes_ioctl.h          # Общий ABI (ядро + userspace)
│
└── userspace/
    ├── CMakeLists.txt
    ├── lib/                    # libquotes_client (статическая обёртка)
    │   ├── CMakeLists.txt
    │   ├── quotes_client.h
    │   └── quotes_client.c
    ├── tools/                  # CLI-утилиты
    │   ├── CMakeLists.txt
    │   ├── quotes_monitor.c    # Живой монитор котировок в терминале
    │   └── quotes_ctl.c        # Управление через ioctl
    └── tests/
        ├── CMakeLists.txt
        ├── test_ring_buffer.c  # Юнит-тест (без ядра)
        └── test_integration.c  # Интеграционный тест (требует модуль)
```

## Требования

- Linux kernel headers: `sudo apt install linux-headers-$(uname -r)`
- GCC, Make, CMake ≥ 3.18

## Сборка

### Только ядро (Kbuild)
```bash
cd kernel
make
```

### Сборка проекта
```bash
cmake -B build
cmake --build build
```


## Загрузка модуля

```bash
sudo insmod kernel/quotes_driver.ko ring_size=64 interval_ms=500
sudo chmod 666 /dev/quotes
```

Или через CMake:
```bash
cmake --build build --target load_module
```

## Использование

```bash
# Простое чтение
cat /dev/quotes

# Монитор (обновляется каждую секунду)
./build/userspace/tools/quotes_monitor

# Управление через ioctl
./build/userspace/tools/quotes_ctl count
./build/userspace/tools/quotes_ctl get 10
./build/userspace/tools/quotes_ctl interval 200
./build/userspace/tools/quotes_ctl clear

# Статистика
cat /proc/quotes_stat
```

## Тесты

```bash
# Юнит-тесты (без модуля ядра)
ctest --test-dir build -E integration -V

# Интеграционные тесты (требует загруженный модуль)
sudo ctest --test-dir build -L integration -V
```

## IOCTL API

| Команда                   | Описание                                   |
|---------------------------|--------------------------------------------|
| `QUOTES_IOC_GET_COUNT`    | Кол-во котировок в ring buffer             |
| `QUOTES_IOC_GET_LATEST`   | Получить N последних `struct quote`        |
| `QUOTES_IOC_SET_INTERVAL` | Изменить интервал генерации (мс)           |
| `QUOTES_IOC_CLEAR`        | Очистить ring buffer                       |

## Параметры модуля

| Параметр      | По умолчанию | Описание                           |
|---------------|--------------|------------------------------------|
| `ring_size`   | 64           | Размер кольцевого буфера           |
| `interval_ms` | 500          | Интервал генерации котировок (мс)  |

## Выгрузка

```bash
sudo rmmod quotes_driver
# или:
cmake --build build --target unload_module
```