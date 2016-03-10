/**
 * Copyright (c) 2014, Airbitz Inc
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms are permitted provided that
 * the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Redistribution or use of modified source code requires the express written
 *    permission of Airbitz Inc.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies,
 * either expressed or implied, of the Airbitz Project.
 */

package co.airbitz.internal;

import co.airbitz.internal.SWIGTYPE_p_bool;
import co.airbitz.internal.SWIGTYPE_p_double;
import co.airbitz.internal.SWIGTYPE_p_int64_t;
import co.airbitz.internal.SWIGTYPE_p_int;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_ParsedUri;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_TxInfo;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_TxOutput;
import co.airbitz.internal.SWIGTYPE_p_uint64_t;
import co.airbitz.internal.tABC_Error;
import co.airbitz.internal.tABC_TxDetails;

public class Jni  {
    static {
        System.loadLibrary("abc");
        System.loadLibrary("airbitz");
    }

    public static native String getStringAtPtr(long pointer);
    public static native byte[] getBytesAtPtr(long pointer, int length);
    public static native int[] getCoreCurrencyNumbers();
    public static native String getCurrencyCode(int currencyNumber);
    public static native String getCurrencyDescription(int currencyNumber);
    public static native long get64BitLongAtPtr(long pointer);
    public static native void set64BitLongAtPtr(long pointer, long value);
    public static native int FormatAmount(long satoshi, long ppchar, long decimalplaces, boolean addSign, long perror);
    public static native int satoshiToCurrency(String jarg1, String jarg2, long satoshi, long currencyp, int currencyNum, long error);
    public static native int coreWatcherLoop(String juuid, long jerrorp);
    public static native long ParseAmount(String jarg1, int decimalplaces);

    public static class pLong extends SWIGTYPE_p_long {
        public pLong(long ptr) {
            super(ptr, false);
        }
    }

    public static class ppTxInfo extends SWIGTYPE_p_p_sABC_TxInfo {
        public ppTxInfo(long ptr) {
            super(ptr, false);
        }
        public long getPtr(SWIGTYPE_p_p_sABC_TxInfo p, long i) {
            return getCPtr(p) + i;
        }
    }

    public static tABC_PasswordRule newPasswordRule(long cPtr) {
        return new tABC_PasswordRule(cPtr, false);
    }

    public static tABC_AccountSettings newAccountSettings(long cPtr) {
        return new tABC_AccountSettings(cPtr, false);
    }

    public static tABC_TxOutput newTxOutput(long cPtr) {
        return new tABC_TxOutput(cPtr, false);
    }

    public static tABC_ParsedUri newParsedUri(long cPtr) {
        return new tABC_ParsedUri(cPtr, false);
    }

    public static SWIGTYPE_p_bool newBool(long cPtr) {
        return new SWIGTYPE_p_bool(cPtr, false);
    }

    public static tABC_AsyncBitCoinInfo newAsyncBitcoinInfo(long cPtr) {
        return new tABC_AsyncBitCoinInfo(cPtr, false);
    }

    public static long getCPtr(tABC_Error obj) {
        return tABC_Error.getCPtr(obj);
    }

    public static long getCPtr(SWIGTYPE_p_int64_t obj) {
        return SWIGTYPE_p_int64_t.getCPtr(obj);
    }

    public static long getCPtr(SWIGTYPE_p_long obj) {
        return SWIGTYPE_p_long.getCPtr(obj);
    }

    public static long getCPtr(SWIGTYPE_p_p_char obj) {
        return SWIGTYPE_p_p_char.getCPtr(obj);
    }

    public static long getCPtr(SWIGTYPE_p_double obj) {
        return SWIGTYPE_p_double.getCPtr(obj);
    }

    public static long getCPtr(SWIGTYPE_p_int obj) {
        return SWIGTYPE_p_int.getCPtr(obj);
    }

    public static long getCPtr(SWIGTYPE_p_bool obj) {
        return SWIGTYPE_p_bool.getCPtr(obj);
    }

    public static long getCPtr(tABC_TxDetails obj) {
        return tABC_TxDetails.getCPtr(obj);
    }

    public static long getCPtr(SWIGTYPE_p_p_sABC_TxOutput obj) {
        return SWIGTYPE_p_p_sABC_TxOutput.getCPtr(obj);
    }

    public static long getCPtr(SWIGTYPE_p_uint64_t obj) {
        return SWIGTYPE_p_uint64_t.getCPtr(obj);
    }
}
