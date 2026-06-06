cmake_minimum_required(VERSION 3.22.1)

project(mediacodecat LANGUAGES CXX)

add_library(mediacodecat SHARED
    mediacodecat_native_hook.cpp
)

target_compile_features(mediacodecat PRIVATE cxx_std_17)

target_link_libraries(mediacodecat
    android
    log
)
