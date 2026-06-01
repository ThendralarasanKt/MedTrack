cmake_minimum_required(VERSION 3.22.1)

project(medtrack_native)

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

add_subdirectory(
    ../../../../third_party/llama.cpp
    ${CMAKE_BINARY_DIR}/llama.cpp
)

add_library(
    medtrack_native
    SHARED
    medtrack_native.cpp
)

find_library(log-lib log)

target_link_libraries(
    medtrack_native
    llama
    ${log-lib}
)
