# cmake/CMakeLists.txt
# Вспомогательные CMake-модули проекта.
#
# Этот каталог не собирает цели сам по себе — он только
# регистрирует путь к модулям, чтобы include() работал
# из любого места дерева сборки.

cmake_minimum_required(VERSION 3.18)

# Добавляем каталог в CMAKE_MODULE_PATH, если он ещё не там.
# Полезно при подключении проекта как подмодуля через add_subdirectory().
list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}")

# Передаём путь наверх (в родительский scope)
set(CMAKE_MODULE_PATH "${CMAKE_MODULE_PATH}" PARENT_SCOPE)

# Документируем доступные модули
message(STATUS "CMake helpers registered:")
message(STATUS "  KernelModule.cmake  — add_kernel_module() function")
