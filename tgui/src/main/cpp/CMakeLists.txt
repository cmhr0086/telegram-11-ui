# tgui: the rlottie + lottie JNI subset of upstream TMessagesProj/jni/CMakeLists.txt.
# Produces libtguilottie.so (loaded by RLottieDrawable) instead of the full libtmessages.
cmake_minimum_required(VERSION 3.22.1)
project(tguilottie C CXX ASM)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--exclude-libs,librlottie.a -Wl,--gc-sections -Wl,-z,max-page-size=16384")

#rlottie
add_library(rlottie STATIC
        rlottie/src/lottie/lottieanimation.cpp
        rlottie/src/lottie/lottieitem.cpp
        rlottie/src/lottie/lottiekeypath.cpp
        rlottie/src/lottie/lottieloader.cpp
        rlottie/src/lottie/lottiemodel.cpp
        rlottie/src/lottie/lottieparser.cpp
        rlottie/src/lottie/lottieproxymodel.cpp
        rlottie/src/vector/freetype/v_ft_math.cpp
        rlottie/src/vector/freetype/v_ft_raster.cpp
        rlottie/src/vector/freetype/v_ft_stroker.cpp
        rlottie/src/vector/pixman/vregion.cpp
        rlottie/src/vector/stb/stb_image.cpp
        rlottie/src/vector/vbezier.cpp
        rlottie/src/vector/vbitmap.cpp
        rlottie/src/vector/vbrush.cpp
        rlottie/src/vector/vcompositionfunctions.cpp
        rlottie/src/vector/vdasher.cpp
        rlottie/src/vector/vdebug.cpp
        rlottie/src/vector/vdrawable.cpp
        rlottie/src/vector/vdrawhelper.cpp
        rlottie/src/vector/vdrawhelper_neon.cpp
        rlottie/src/vector/velapsedtimer.cpp
        rlottie/src/vector/vimageloader.cpp
        rlottie/src/vector/vinterpolator.cpp
        rlottie/src/vector/vmatrix.cpp
        rlottie/src/vector/vpainter.cpp
        rlottie/src/vector/vpath.cpp
        rlottie/src/vector/vpathmesure.cpp
        rlottie/src/vector/vraster.cpp
        rlottie/src/vector/vrect.cpp
        rlottie/src/vector/vrle.cpp)
target_compile_options(rlottie PUBLIC
        -Wall -fno-rtti -finline-functions -ffast-math -Os -fno-unwind-tables -fno-asynchronous-unwind-tables -Wnon-virtual-dtor -Woverloaded-virtual -Wno-unused-parameter -fvisibility=hidden)
set_target_properties(rlottie PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(rlottie PUBLIC
        HAVE_PTHREAD NDEBUG)
target_include_directories(rlottie PUBLIC
        ./
        rlottie/inc
        rlottie/src/vector/
        rlottie/src/vector/pixman
        rlottie/src/vector/freetype
        rlottie/src/vector/stb)

# upstream links pixman's NEON fill asm (pixman-arm*-neon-asm.S) with -fno-integrated-as, i.e.
# GNU as from NDK 21. NDK r24+ only ships clang's integrated assembler, which can't parse those
# macros, so tgui builds every ABI with rlottie's portable C fill path (vdrawhelper.cpp).
# clang predefines __ARM_NEON__ on armeabi-v7a, which would select the asm path; undefine it.
if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(rlottie PRIVATE
            -U__ARM_NEON__)
endif()

add_library(tguilottie SHARED
        lottie.cpp)
target_compile_options(tguilottie PRIVATE
        -ffunction-sections -fdata-sections -fvisibility=hidden -Os)
target_link_libraries(tguilottie
        rlottie
        jnigraphics
        log)
