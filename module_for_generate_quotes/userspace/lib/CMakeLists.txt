add_library(quotes_client STATIC
    quotes_client.c
)

target_include_directories(quotes_client
    PUBLIC
        "${CMAKE_SOURCE_DIR}/kernel"    # quotes_ioctl.h
        "${CMAKE_CURRENT_SOURCE_DIR}"   # quotes_client.h
)

target_compile_options(quotes_client PRIVATE
    -Wall -Wextra -Wpedantic
    -Wno-unused-parameter
)

add_library(quotes::client ALIAS quotes_client)
