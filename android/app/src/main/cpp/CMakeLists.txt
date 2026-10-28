cmake_minimum_required(VERSION 3.22.1)
project(snoopar_jni LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../core)

# Shared Core C++ source files
set(CORE_SOURCES
    ${CORE_DIR}/src/rf_model.cpp
    ${CORE_DIR}/src/bayesian_grid.cpp
    ${CORE_DIR}/src/trajectory_analyzer.cpp
    ${CORE_DIR}/src/engine.cpp
    ${CORE_DIR}/src/snoopar_core_c.cpp
    snoopar_jni.cpp
)

add_library(snoopar_jni SHARED ${CORE_SOURCES})

target_include_directories(snoopar_jni PRIVATE
    ${CORE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)

target_link_libraries(snoopar_jni PRIVATE
    ${log-lib}
)
