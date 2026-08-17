cmake_minimum_required(VERSION 3.22.1)
project(local_character LANGUAGES C CXX)

set(LLAMA_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")
if (NOT EXISTS "${LLAMA_ROOT}/include/llama.h")
    message(FATAL_ERROR "llama.cpp is missing. Run scripts/fetch-llama.ps1 from the repository root.")
endif()

# Runtime CPU variants are shared backends selected from Android HWCAP. Without them,
# arm64-v8a falls back to baseline ARMv8 and misses dot-product/FP16 instructions.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)

# Match llama.cpp's official Android binding: optimized runtime-dispatched ARM kernels
# with a generic x86_64 fallback. The produced arm64 binary still runs on older ARMv8 CPUs.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM" CACHE STRING "" FORCE)
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
    set(GGML_OPENMP ON CACHE BOOL "" FORCE)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(GGML_SYSTEM_ARCH "x86" CACHE STRING "" FORCE)
    set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
    set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory("${LLAMA_ROOT}" "${CMAKE_CURRENT_BINARY_DIR}/llama" EXCLUDE_FROM_ALL)

add_library(local_character SHARED llama_jni.cpp)
target_compile_definitions(local_character PRIVATE LLAMA_BUILD_TAG="b10434")
target_include_directories(local_character PRIVATE
    "${LLAMA_ROOT}"
    "${LLAMA_ROOT}/common"
    "${LLAMA_ROOT}/include"
    "${LLAMA_ROOT}/ggml/include"
    "${LLAMA_ROOT}/ggml/src"
)
target_link_libraries(local_character PRIVATE llama llama-common android log)
