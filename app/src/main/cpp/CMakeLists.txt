cmake_minimum_required(VERSION 3.22.1)
project(paddle_vision)

add_library(paddle_vision SHARED native_bridge.cpp)

find_library(log-lib log)

target_link_libraries(
    paddle_vision
    ${log-lib}
)
