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

package co.airbitz.api;

class Jni  {
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
    public static native int coreDataSyncAccount(String jusername, String jpassword, long jerrorp);
    public static native int coreDataSyncWallet(String jusername, String jpassword, String juuid, long jerrorp);
    public static native int coreSweepKey(String jusername, String jpassword, String juuid, String wif, long ppchar, long jerrorp);
    public static native int coreWatcherLoop(String juuid, long jerrorp);
    public static native long ParseAmount(String jarg1, int decimalplaces);

    static class pLong extends SWIGTYPE_p_long {
        public pLong(long ptr) {
            super(ptr, false);
        }
    }

    static class ppTxInfo extends SWIGTYPE_p_p_sABC_TxInfo {
        public ppTxInfo(long ptr) {
            super(ptr, false);
        }
        public long getPtr(SWIGTYPE_p_p_sABC_TxInfo p, long i) {
            return getCPtr(p) + i;
        }
    }
}
