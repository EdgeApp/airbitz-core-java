LOCAL_PATH := $(call my-dir)

# Tell the build system about libabc.a:
include $(CLEAR_VARS)
LOCAL_MODULE := airbitz-core
LOCAL_SRC_FILES := build/$(TARGET_ARCH_ABI)/libabc.so
include $(PREBUILT_SHARED_LIBRARY)

# Build the JNI wrapper:
include $(CLEAR_VARS)
LOCAL_MODULE := airbitz
LOCAL_SRC_FILES := build/ABC_wrap.c ABC_util.c
LOCAL_SHARED_LIBRARIES := airbitz-core
LOCAL_LDLIBS  := -llog
LOCAL_C_INCLUDES := build/includes
include $(BUILD_SHARED_LIBRARY)
