cmake_minimum_required(VERSION 3.22.1)

project("whisper-android")

# This is a placeholder for linking the actual whisper.cpp repository.
# You will need to clone `https://github.com/ggerganov/whisper.cpp.git` into `whisper/`
# inside this cpp directory before compilation.

# Include the whisper.cpp subdirectory
add_subdirectory(whisper)

# Define our JNI wrapper library
add_library(
        whisper-jni
        SHARED
        whisper-jni.cpp)

# Link the standard Android libraries and the whisper core library
find_library(
        log-lib
        log)

# Link against the whisper library defined in whisper.cpp's CMakeLists.txt
target_link_libraries(whisper-jni whisper ${log-lib})
