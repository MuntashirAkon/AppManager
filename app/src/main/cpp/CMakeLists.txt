cmake_minimum_required(VERSION 3.4.1)

project(AppManager LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)

# Global compiler flags
set(COMMON_C_FLAGS "-Werror=format -fdata-sections -ffunction-sections -fno-exceptions -fno-rtti -fno-threadsafe-statics")
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(COMMON_C_FLAGS "${COMMON_C_FLAGS} -O2 -fvisibility=hidden -fvisibility-inlines-hidden")
endif()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_C_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_C_FLAGS}")

# Global linker flags
set(BASE_LINK_FLAGS "-Wl,--hash-style=both -Wl,--build-id=none")
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    message("Building Release...")

    set(BASE_LINK_FLAGS "${BASE_LINK_FLAGS} -Wl,-exclude-libs,ALL -Wl,--gc-sections")
else()
    message("Building Debug...")

    add_definitions(-DDEBUG)
endif ()

find_library(log-lib log)

# BEGIN libam.so
add_library(am SHARED
        AhoCorasick.cpp
        io_github_muntashirakon_algo_AhoCorasick.cpp
        io_github_muntashirakon_AppManager_utils_CpuUtils.cpp
        io_github_muntashirakon_compat_system_OsCompat.cpp)

target_link_libraries(am ${log-lib})

set_target_properties(am PROPERTIES LINK_FLAGS "${BASE_LINK_FLAGS}")

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET am POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "$<TARGET_FILE:am>")
endif ()

# END libam.so

# BEGIN librun_server.so
add_executable(run_server run_server.c)

# Force the output to be packaged as a .so file
set_target_properties(run_server PROPERTIES
        OUTPUT_NAME "run_server"
        PREFIX "lib"
        SUFFIX ".so"
        COMPILE_FLAGS "-fPIE -fPIC"
        LINK_FLAGS "-pie ${BASE_LINK_FLAGS}"
)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET run_server POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "$<TARGET_FILE:run_server>")
endif ()
# END librun_server.so
