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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import co.airbitz.internal.Jni;
import co.airbitz.internal.core;

public class Currencies {
    private int[] mCurrencyNumbers;
    private Map<Integer, String> mCurrencySymbolCache = new HashMap<>();
    private Map<Integer, String> mCurrencyCodeCache = new HashMap<>();
    private Map<Integer, String> mCurrencyDescriptionCache = new HashMap<>();

    private static Currencies sSingleton;

    public static Currencies instance() {
        if (null == sSingleton) {
            sSingleton = new Currencies();
        }
        return sSingleton;
    }

    public String currencyCodeLookup(int currencyNum) {
        String cached = mCurrencyCodeCache.get(currencyNum);
        if (cached != null) {
            return cached;
        }

        String code = Jni.getCurrencyCode(currencyNum);
        if(code != null) {
            mCurrencyCodeCache.put(currencyNum, code);
            return code;
        }

        return "";
    }

    public String currencyDescriptionLookup(int currencyNum)
    {
        String cached = mCurrencyDescriptionCache.get(currencyNum);
        if (cached != null) {
            return cached;
        }

        String description = Jni.getCurrencyDescription(currencyNum);
        if(description != null) {
            mCurrencyDescriptionCache.put(currencyNum, description);
            return description;
        }

        return "";
    }

    public String currencySymbolLookup(int currencyNum)
    {
        String cached = mCurrencySymbolCache.get(currencyNum);
        if (cached != null) {
            return cached;
        }

        try {
            String code = currencyCodeLookup(currencyNum);
            String symbol  = Currency.getInstance(code).getSymbol();
            if(symbol != null) {
                mCurrencySymbolCache.put(currencyNum, symbol);
                return symbol;
            }
            else {
                AirbitzCore.debugLevel(1, "Bad currency code: " + code);
                return "";
            }
        }
        catch (Exception e) {
            return "";
        }
    }

    public String getCurrencyDenomination(int currencyNum) {
        return currencySymbolLookup(currencyNum);
    }

    public int[] getCurrencyNumberArray() {
        ArrayList<Integer> intKeys = new ArrayList<Integer>(mCurrencyCodeCache.keySet());
        int[] ints = new int[intKeys.size()];
        int i = 0;
        for (Integer n : intKeys) {
            ints[i++] = n;
        }
        return ints;
    }

    public String getCurrencyAcronym(int currencyNum) {
        return currencyCodeLookup(currencyNum);
    }

    public List<String> getCurrencyCodeAndDescriptionArray() {
        initCurrencies();
        List<String> strings = new ArrayList<>();
        // Populate all codes and lists and the return list
        for(Integer number : mCurrencyNumbers) {
            String code = currencyCodeLookup(number);
            String description = currencyDescriptionLookup(number);
            String symbol = currencySymbolLookup(number);
            strings.add(code + " - " + description);
        }
        return strings;
    }

    public List<String> getCurrencyCodeArray() {
        initCurrencies();
        List<String> strings = new ArrayList<>();
        // Populate all codes and lists and the return list
        for(Integer number : mCurrencyNumbers) {
            String code = currencyCodeLookup(number);
            strings.add(code);
        }
        return strings;
    }

    public void initCurrencies() {
        if (mCurrencyNumbers == null) {
            mCurrencyNumbers = Jni.getCoreCurrencyNumbers();
            mCurrencySymbolCache = new HashMap<>();
            mCurrencyCodeCache = new HashMap<>();
            mCurrencyDescriptionCache = new HashMap<>();
            for(Integer number : mCurrencyNumbers) {
                currencyCodeLookup(number);
                currencyDescriptionLookup(number);
                currencySymbolLookup(number);
            }
        }
    }

    private int findCurrencyIndex(int currencyNum) {
        for(int i=0; i< mCurrencyNumbers.length; i++) {
            if (currencyNum == mCurrencyNumbers[i])
                return i;
        }
        AirbitzCore.debugLevel(1, "CurrencyIndex not found, using default");
        return 10; // default US
    }

    public int getCurrencyNumberFromCode(String currencyCode) {
        Currencies.instance().initCurrencies();
        int index = -1;
        for (int i=0; i< mCurrencyNumbers.length; i++) {
            if (currencyCode.equals(currencyCodeLookup(mCurrencyNumbers[i]))) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            return mCurrencyNumbers[index];
        }
        return 840;
    }

    public int defaultCurrencyNum() {
        initCurrencies();

        Locale locale = Locale.getDefault();
        Currency currency = Currency.getInstance(locale);
        Map<Integer, String> supported = mCurrencyCodeCache;
        if (supported.containsValue(currency.getCurrencyCode())) {
            int number = getCurrencyNumberFromCode(currency.getCurrencyCode());
            return number;
        } else {
            return 840;
        }
    }

    public String getCurrencyCode(int currencyNumber) {
        return Jni.getCurrencyCode(currencyNumber);
    }

    public int[] getCoreCurrencyNumbers() {
        return Jni.getCoreCurrencyNumbers();
    }
}
