cmake_minimum_required(VERSION 3.18)

if(CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
    project(quotes_userspace VERSION 1.0.0 LANGUAGES C)
    set(CMAKE_C_STANDARD 11)
    set(CMAKE_C_STANDARD_REQUIRED ON)
    include_directories("${CMAKE_CURRENT_SOURCE_DIR}/../kernel")
endif()

if(NOT DEFINED BUILD_TOOLS)
    option(BUILD_TOOLS "Build userspace CLI tools" ON)
endif()
if(NOT DEFINED BUILD_TESTS)
    option(BUILD_TESTS "Build tests" ON)
endif()

add_subdirectory(lib)

if(BUILD_TOOLS)
    add_subdirectory(tools)
endif()

if(BUILD_TESTS)
    if(NOT CMAKE_TESTING_ENABLED)
        enable_testing()
    endif()
    add_subdirectory(tests)
endif()
