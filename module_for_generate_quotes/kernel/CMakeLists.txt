# kernel/CMakeLists.txt
# Сборка Linux kernel module quotes_driver.ko через Kbuild.
#
# Может использоваться двумя способами:
#   1. Как часть дерева через корневой CMakeLists.txt (add_subdirectory)
#   2. Как standalone: cmake -S kernel/ -B kernel/build

cmake_minimum_required(VERSION 3.18)

# Standalone-режим: объявляем проект и грузим хелпер сами
if(CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
    project(quotes_driver_kernel VERSION 1.0.0 LANGUAGES C)
    list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../cmake")
    include(KernelModule)
endif()

# ── Установка заголовка ABI ──────────────────────────────────────────
install(FILES quotes_ioctl.h
        DESTINATION "${CMAKE_INSTALL_INCLUDEDIR}/quotes"
        COMPONENT   devel)

# ── Установка собранного модуля ──────────────────────────────────────
install(FILES "${CMAKE_CURRENT_SOURCE_DIR}/quotes_driver.ko"
        DESTINATION "${CMAKE_INSTALL_LIBDIR}/modules"
        COMPONENT   kernel
        OPTIONAL)                   # OPTIONAL: не падать, если .ko нет

# ── Вспомогательные цели загрузки/выгрузки ──────────────────────────
add_custom_target(load
    COMMAND sudo insmod "${CMAKE_CURRENT_SOURCE_DIR}/quotes_driver.ko"
            ring_size=64 interval_ms=500
    COMMAND sudo chmod 666 /dev/quotes
    DEPENDS quotes_driver_kbuild
    COMMENT "insmod quotes_driver.ko"
    VERBATIM
)

add_custom_target(unload
    COMMAND sudo rmmod quotes_driver || true
    COMMENT "rmmod quotes_driver"
    VERBATIM
)

add_custom_target(reload
    COMMAND ${CMAKE_MAKE_PROGRAM} unload
    COMMAND ${CMAKE_MAKE_PROGRAM} load
    COMMENT "Reloading quotes_driver.ko"
    VERBATIM
)
