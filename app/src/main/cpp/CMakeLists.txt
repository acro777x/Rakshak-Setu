cmake_minimum_required(VERSION 3.22.1)

project("whisper-android")

# This is a placeholder for linking the actual whisper.cpp repository.
# You will need to clone `https://github.com/ggerganov/whisper.cpp.git` into `whisper/`
# inside this cpp directory before compilation.

# add_subdirectory(whisper)

# Define our JNI wrapper library
add_library(
        whisper
        SHARED
        whisper-jni.cpp)

# Link the standard Android libraries and the whisper core library
find_library(
        log-lib
        log)

# target_link_libraries(whisper whisper_core ${log-lib})
target_link_libraries(whisper ${log-lib})
