#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

telephony_src_files := $(call all-java-files-under,java/android/telephony/)
telephony_src_files += $(call all-java-files-under,java/android/telephony/gsm/)
telephony_src_files += $(call all-java-files-under,java/android/telephony/cdma/)
telephony_src_files += $(call all-java-files-under,java/com/android/internal/telephony/)
telephony_src_files += $(call all-java-files-under,java/com/android/internal/telephony/gsm/)
telephony_src_files += $(call all-java-files-under,java/com/android/internal/telephony/cdma/)

LOCAL_SRC_FILES := $(telephony_src_files)

$(info $(LOCAL_SRC_FILES) ...telephony....)

LOCAL_MODULE := telephony_sp
include $(BUILD_JAVA_LIBRARY)

