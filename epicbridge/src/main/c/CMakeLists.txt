cmake_minimum_required(VERSION 3.22.1)
project(epic_python C)

set(PYTHON_VERSION "3.15")
set(PYTHON_PREFIX_DIR "" CACHE PATH "CPython Android prefix")

if(PYTHON_PREFIX_DIR AND EXISTS "${PYTHON_PREFIX_DIR}/include/python${PYTHON_VERSION}/Python.h"
   AND EXISTS "${PYTHON_PREFIX_DIR}/lib/libpython${PYTHON_VERSION}.so")
    message(STATUS "Building Epic Bridge against CPython at ${PYTHON_PREFIX_DIR}")
    add_library(epic_python SHARED epic_python.c)
    target_include_directories(epic_python PRIVATE
        "${PYTHON_PREFIX_DIR}/include/python${PYTHON_VERSION}")
    target_link_directories(epic_python PRIVATE "${PYTHON_PREFIX_DIR}/lib")
    target_link_libraries(epic_python PRIVATE log python${PYTHON_VERSION})
    target_compile_definitions(epic_python PRIVATE PYTHON_VERSION_STRING="${PYTHON_VERSION}")
else()
    message(WARNING "CPython Android prefix not supplied; building an explicit not-ready stub")
    add_library(epic_python SHARED epic_python_stub.c)
    target_link_libraries(epic_python PRIVATE log)
endif()
