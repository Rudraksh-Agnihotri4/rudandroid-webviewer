cmake_minimum_required(VERSION 3.10)
project(edgeproc)

add_library(
        edgeproc
        SHARED
        edgeproc.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        edgeproc
        ${log-lib}
)
