add_executable(test_ring_buffer test_ring_buffer.c)
target_include_directories(test_ring_buffer PRIVATE
    "${CMAKE_SOURCE_DIR}/kernel"
)
target_compile_options(test_ring_buffer PRIVATE -Wall -Wextra)
add_test(NAME ring_buffer COMMAND test_ring_buffer)

add_executable(test_integration test_integration.c)
target_link_libraries(test_integration PRIVATE quotes::client)
target_compile_options(test_integration PRIVATE -Wall -Wextra)

add_test(NAME integration COMMAND test_integration)
set_tests_properties(integration PROPERTIES LABELS "integration")
