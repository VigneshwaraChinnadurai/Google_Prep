cmake_minimum_required(VERSION 3.22.1)
project("leetcodechecker_llama")

# C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android-specific optimizations
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fexceptions -frtti")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")

# GGML/llama.cpp options for Android
set(GGML_NATIVE OFF CACHE BOOL "Disable native optimizations for cross-compile")
set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP for Android")
set(GGML_CUDA OFF CACHE BOOL "Disable CUDA")
set(GGML_METAL OFF CACHE BOOL "Disable Metal")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static libs")
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Don't build examples")
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Don't build tests")
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Don't build server")

# Add llama.cpp subdirectory
add_subdirectory(llama.cpp)

# Create the JNI bridge library
add_library(llama_jni SHARED llama_jni.cpp)

# Link against llama and Android libraries
target_link_libraries(llama_jni
    llama
    android
    log
)

# Include llama.cpp headers
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)
