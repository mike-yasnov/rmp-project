cmake_minimum_required(VERSION 3.18)
project(quotes_driver
    VERSION     1.0.0
    DESCRIPTION "Linux kernel char device: stock quote generator"
    LANGUAGES   C
)

# ── Standard settings ────────────────────────────────────────────────
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)   # для clangd / IDEs

# ── Пути ────────────────────────────────────────────────────────────
set(KERNEL_DIR   "${CMAKE_SOURCE_DIR}/kernel")
set(USPACE_DIR   "${CMAKE_SOURCE_DIR}/userspace")

# Shared IOCTL header: ядро и userspace оба видят kernel/
include_directories(${KERNEL_DIR})

# ── Опции ───────────────────────────────────────────────────────────
option(BUILD_TESTS    "Build unit/integration tests"   ON)
option(BUILD_TOOLS    "Build userspace CLI tools"      ON)
option(ENABLE_ASAN    "Enable AddressSanitizer (debug)" OFF)

# ── Санитайзеры ─────────────────────────────────────────────────────
if(ENABLE_ASAN)
    add_compile_options(-fsanitize=address,undefined -fno-omit-frame-pointer)
    add_link_options   (-fsanitize=address,undefined)
endif()

# ── Подмодули ───────────────────────────────────────────────────────
add_subdirectory(cmake)       # регистрирует CMAKE_MODULE_PATH
add_subdirectory(kernel)      # собирает quotes_driver.ko через Kbuild
add_subdirectory(userspace)   # lib → tools → tests

# ── Установка (верхний уровень — агрегирует компоненты) ──────────────
# Детальные правила install() живут в kernel/ и userspace/lib/.
# Здесь только удобный алиас для сборки + загрузки одной командой:
#   cmake --build build --target load_module
add_custom_target(load_module
    COMMAND ${CMAKE_MAKE_PROGRAM} -C "${CMAKE_BINARY_DIR}/kernel" load
    COMMENT "Load kernel module (delegates to kernel/CMakeLists.txt)"
    VERBATIM
)

add_custom_target(unload_module
    COMMAND ${CMAKE_MAKE_PROGRAM} -C "${CMAKE_BINARY_DIR}/kernel" unload
    COMMENT "Unload kernel module (delegates to kernel/CMakeLists.txt)"
    VERBATIM
)
