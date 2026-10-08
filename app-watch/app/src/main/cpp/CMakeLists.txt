cmake_minimum_required(VERSION 3.22.1)
project("xiaozhi_audio")

find_package(oboe REQUIRED CONFIG)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(OPUS_BUILD_SHARED_LIBRARY OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
add_subdirectory(opus)
target_compile_options(opus PRIVATE -O2)
add_library(kiumo_opus SHARED kiumo_opus.cpp)
target_compile_options(kiumo_opus PRIVATE -O2)
target_link_libraries(kiumo_opus PRIVATE opus log)

add_library(xiaozhi_audio SHARED
    oboe_player.cpp
)
target_compile_options(xiaozhi_audio PRIVATE -O2)

target_link_libraries(xiaozhi_audio
    oboe::oboe
    android
    log
)
