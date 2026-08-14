cmake_minimum_required(VERSION 3.22.1)

project("geminimine" CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(game-activity REQUIRED CONFIG)

add_library(geminimine SHARED
    main.cpp
)

target_compile_options(geminimine PRIVATE
    -Wall
    -Wextra
    -Wpedantic
)

target_link_libraries(geminimine PRIVATE
    -Wl,--whole-archive
    game-activity::game-activity_static
    -Wl,--no-whole-archive
    android
    log
)
