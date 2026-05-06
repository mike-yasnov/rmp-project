add_executable(quotes_monitor quotes_monitor.c)
target_link_libraries(quotes_monitor PRIVATE quotes::client)
target_compile_options(quotes_monitor PRIVATE -Wall -Wextra)


add_executable(quotes_ctl quotes_ctl.c)
target_link_libraries(quotes_ctl PRIVATE quotes::client)
target_compile_options(quotes_ctl PRIVATE -Wall -Wextra)

install(TARGETS quotes_monitor quotes_ctl
        RUNTIME DESTINATION bin)
