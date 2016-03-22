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

package co.airbitz.core;

import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import co.airbitz.internal.Jni;
import co.airbitz.internal.core;

/**
 * Used internally only.
 */
class Currencies {
    static Map<String, CoreCurrency> mNumberIndex;
    static Map<String, CoreCurrency> mCodeIndex;

    private int[] mCurrencyNumbers;
    private Map<Integer, String> mCurrencySymbolCache = new HashMap<>();
    private Map<Integer, String> mCurrencyDescriptionCache = new HashMap<>();

    private static Currencies sSingleton;

    public static Currencies instance() {
        if (null == sSingleton) {
            sSingleton = new Currencies();
        }
        return sSingleton;
    }

    protected Currencies() {
        int[] nums = Jni.getCoreCurrencyNumbers();
        mCodeIndex = new LinkedHashMap<String, CoreCurrency>();
        mNumberIndex = new LinkedHashMap<String, CoreCurrency>();
        for (Integer number : nums) {
            CoreCurrency c = new CoreCurrency();
            c.currencyNum = number;
            c.code = Jni.getCurrencyCode(number);
            c.description = Jni.getCurrencyDescription(number);
            try {
                c.symbol = Currency.getInstance(c.code).getSymbol();
            } catch (IllegalArgumentException e) {
                c.symbol = "";
            }
            mCodeIndex.put(c.code, c);
            mNumberIndex.put(String.valueOf(c.currencyNum), c);
        }
    }

    List<CoreCurrency> currencies() {
        return new ArrayList<CoreCurrency>(mCodeIndex.values());
    }

    CoreCurrency defaultCurrency() {
        Locale locale = Locale.getDefault();
        Currency currency = Currency.getInstance(locale);
        if (mCodeIndex.containsValue(currency.getCurrencyCode())) {
            return mCodeIndex.get(currency.getCurrencyCode());
        } else {
            return mCodeIndex.get("USD");
        }
    }

    public String currencySymbol(String code) {
        CoreCurrency e = mCodeIndex.get(code);
        if (e != null) {
            return e.symbol;
        } else {
            return "";
        }
    }

    CoreCurrency lookup(String code) {
        return mCodeIndex.get(code);
    }

    protected int map(String code) {
        if (mCodeIndex.containsKey(code)) {
            return mCodeIndex.get(code).currencyNum;
        } else {
            return 840;
        }
    }

    protected String map(int num) {
        if (mNumberIndex.containsKey(String.valueOf(num))) {
            return mNumberIndex.get(String.valueOf(num)).code;
        } else {
            return "USD";
        }
    }
}
