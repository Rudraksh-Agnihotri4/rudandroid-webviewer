cmake_minimum_required(VERSION 3.10)
project(edgeproc)

add_library(
        edgeproc
        SHARED
        edgeproc.cpp
)

# Import prebuilt OpenCV shared library from jniLibs
add_library(
        opencv_java4
        SHARED
        IMPORTED
)

set_target_properties(
        opencv_java4
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopencv_java4.so
)

# Include OpenCV headers (copied into cpp/includes)
target_include_directories(
        edgeproc
        PRIVATE
        ${CMAKE_SOURCE_DIR}/includes
)

find_library(
        log-lib
        log
)

target_link_libraries(
        edgeproc
        opencv_java4
        ${log-lib}
)
