cmake_minimum_required(VERSION 3.16)
project(snoopar_core VERSION 0.1.0 LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS ON)

# Source files
set(CORE_SOURCES
    src/rf_model.cpp
    src/bayesian_grid.cpp
    src/trajectory_analyzer.cpp
    src/engine.cpp
    src/snoopar_core_c.cpp
)

# Static Library (for embedding in Android NDK / iOS Xcode)
add_library(snoopar_core STATIC ${CORE_SOURCES})
target_include_directories(snoopar_core PUBLIC 
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)

# Shared Library (for dynamic linking / Python ctypes / JNI)
add_library(snoopar_core_shared SHARED ${CORE_SOURCES})
set_target_properties(snoopar_core_shared PROPERTIES 
    OUTPUT_NAME "snoopar_core"
    ARCHIVE_OUTPUT_NAME "snoopar_core_import"
)
target_include_directories(snoopar_core_shared PUBLIC 
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)

# Test Executable
add_executable(snoopar_test tests/test_engine.cpp)
target_link_libraries(snoopar_test PRIVATE snoopar_core)

enable_testing()
add_test(NAME CoreEngineTests COMMAND snoopar_test)
