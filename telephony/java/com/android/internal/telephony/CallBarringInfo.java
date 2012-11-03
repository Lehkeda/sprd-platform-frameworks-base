/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import android.telephony.PhoneNumberUtils;

/**
 * See also RIL_CallBarringInfo in include/telephony/ril.h
 *
 * {@hide}
 */
public class CallBarringInfo {
    public int             status;      /*mode, 1 = active, 0 = not active */
    public int             reason;      /* fac, from TS 27.007 7.11 "reason" */
    public int             serviceClass; /* class, Sum of CommandsInterface.SERVICE_CLASS */
    public String          password;      /* "number" from TS 27.007 7.11 */

    public String toString() {
        return super.toString() + (status == 0 ? " not active " : " active ")
            + " reason: " + reason
            + " serviceClass: " + serviceClass;

    }
}

