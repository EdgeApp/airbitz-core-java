LOCAL_PATH := $(call my-dir)

# Tell the build system about libc++_shared.so:
include $(CLEAR_VARS)
LOCAL_MODULE := libcpp
LOCAL_SRC_FILES := build/$(TARGET_ARCH_ABI)/libc++_shared.so
include $(PREBUILT_SHARED_LIBRARY)

# Tell the build system about libabc.a:
include $(CLEAR_VARS)
LOCAL_MODULE := airbitz-core
LOCAL_SRC_FILES := build/$(TARGET_ARCH_ABI)/libabc.so
include $(PREBUILT_SHARED_LIBRARY)

# Build the JNI wrapper:
include $(CLEAR_VARS)
LOCAL_MODULE := airbitz
LOCAL_SRC_FILES := build/ABC_wrap.c ABC_util.c
LOCAL_SHARED_LIBRARIES := libcpp airbitz-core
LOCAL_LDLIBS  := -llog -latomic
LOCAL_C_INCLUDES := build/includes
include $(BUILD_SHARED_LIBRARY)
