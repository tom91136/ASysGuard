cmake_minimum_required(VERSION 3.16 FATAL_ERROR)
project(asysguard)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_STANDARD_REQUIRED ON)


include(${CMAKE_SOURCE_DIR}/cmake/CPM.cmake)

CPMAddPackage(
        GITHUB_REPOSITORY yhirose/cpp-httplib
        VERSION 0.16.3
        OPTIONS
        "HTTPLIB_COMPILE ON" "HTTPLIB_USE_OPENSSL_IF_AVAILABLE ON" "HTTPLIB_REQUIRE_OPENSSL ON"
)
CPMAddPackage(
        GITHUB_REPOSITORY nlohmann/json
        VERSION 3.11.3
        OPTIONS
        "JSON_BuildTests OFF"
)


find_package(PkgConfig REQUIRED)
pkg_check_modules(WAYLAND_CLIENT REQUIRED wayland-client)


#  === setup wl protocol project ===
file(GLOB PROTOCOL_FILES ${CMAKE_SOURCE_DIR}/protocols/*.xml)
set(WL_HEADER_DIR "${CMAKE_BINARY_DIR}/wl-protocols")
file(MAKE_DIRECTORY "${WL_HEADER_DIR}")
foreach (xml_file ${PROTOCOL_FILES})
    message(STATUS "Adding protocol ${xml_file}")
    get_filename_component(protocol_name ${xml_file} NAME_WE)
    set(header_file ${WL_HEADER_DIR}/${protocol_name}-client-protocol.h)
    set(source_file ${CMAKE_BINARY_DIR}/${protocol_name}-client-protocol.c)
    add_custom_command(
            OUTPUT ${header_file}
            COMMAND wayland-scanner client-header ${xml_file} ${header_file}
            DEPENDS ${xml_file}
            COMMENT "Generating Wayland protocol header: ${header_file}"
    )
    add_custom_command(
            OUTPUT ${source_file}
            COMMAND wayland-scanner private-code ${xml_file} ${source_file}
            DEPENDS ${xml_file}
            COMMENT "Generating Wayland protocol impl.: ${source_file}"
    )
    list(APPEND WL_GENERATED_HEADERS ${header_file})
    list(APPEND WL_GENERATED_SOURCES ${source_file})
endforeach ()
add_library(wl-protocols ${WL_GENERATED_SOURCES} ${WL_GENERATED_HEADERS})
target_include_directories(wl-protocols PUBLIC ${WL_HEADER_DIR})
#  ======

add_executable(${PROJECT_NAME} main.cpp)
target_precompile_headers(${PROJECT_NAME} PRIVATE
        [["httplib.h"]]
        [["nlohmann/json.hpp"]]


        <iostream>
)
target_include_directories(${PROJECT_NAME} PRIVATE include)
target_link_libraries(${PROJECT_NAME} PRIVATE wl-protocols wayland-client httplib::httplib nlohmann_json::nlohmann_json)
target_compile_options(${PROJECT_NAME}
        PUBLIC
        -Wall
        -Wextra
        -Wcast-align
        -Wpedantic
        -Werror=return-type
        -Wno-unused-parameter
        -Wno-ignored-attributes

        "$<$<CONFIG:Release>:-O3;-march=native;-mtune=native>"
)
target_link_options(${PROJECT_NAME} PRIVATE
        "$<$<CONFIG:Release>:-static-libgcc;-static-libstdc++>")


set(SANITISE ON)

if (SANITISE)
    set(DEBUG_OPTIONS -O2 -fno-omit-frame-pointer -fsanitize=address -fsanitize=undefined)
    target_compile_options(${PROJECT_NAME} PUBLIC "$<$<CONFIG:DEBUG>:${DEBUG_OPTIONS}>")
    target_link_options(${PROJECT_NAME} PUBLIC "$<$<CONFIG:DEBUG>:${DEBUG_OPTIONS}>")
endif ()


install(TARGETS ${PROJECT_NAME} CONFIGURATIONS Release)

if (NOT AUTOSTART_DIR)
    set(XDG_AUTOSTART_DIR $ENV{HOME}/.config/autostart)
    if (EXISTS ${XDG_AUTOSTART_DIR})
        set(AUTOSTART_DIR ${XDG_AUTOSTART_DIR})
    endif ()
endif ()

if (AUTOSTART_DIR)
    message(STATUS "AUTOSTART_DIR is set to ${AUTOSTART_DIR}")
    configure_file(asysguard.desktop.in asysguard.desktop @ONLY)
    add_custom_target(install-autostart-server
            DEPENDS ${PROJECT_NAME}
            COMMENT "Copying .desktop file to ${AUTOSTART_DIR}..."
            COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_BINARY_DIR}/asysguard.desktop ${AUTOSTART_DIR}/.
    )
    if (EXISTS "${CMAKE_SOURCE_DIR}/config.json")
        add_custom_target(install-autostart-server-config
                DEPENDS ${PROJECT_NAME}
                COMMENT "Copying .json file to ${AUTOSTART_DIR}..."
                COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_BINARY_DIR}/config.json ${AUTOSTART_DIR}/.
        )
    endif ()
else ()
    message(STATUS "AUTOSTART_DIR not set, the install targets won't install XDG autostart files")
endif ()


