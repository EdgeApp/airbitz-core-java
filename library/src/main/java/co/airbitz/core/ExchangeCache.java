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

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_double;
import co.airbitz.internal.SWIGTYPE_p_int64_t;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_Error;
import co.airbitz.internal.tABC_CC;

/**
 * ExchangeCache provides functions to deal with converting satoshis to fiat
 * and back. Currency exchange rate updates can also be requested from {@link
 * ExchangeCache #update update}.
 */
public class ExchangeCache {
    /**
     * Request an update to a currency cache value.
     * @param account the account whose exchange source will be used
     * @param currency the fiat code such as USD or EUR.
     */
    public void update(final Account account, final String currency) {
        int num = Currencies.instance().map(currency);
        tABC_Error error = new tABC_Error();
        core.ABC_RequestExchangeRateUpdate(
            account.username(), account.password(), num, error);
    }

    /**
     * Convert a satoshis to a fiat currency value.
     * @param satoshi the satoshis to be converted to fiat
     * @param currency the fiat code such as USD or EUR.
     * @return a fiat value in the given currency code.
     */
    public double satoshiToCurrency(long satoshi, String currency) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_double amountFiat = core.new_doublep();

        int currencyNum = Currencies.instance().map(currency);
        long out = Jni.satoshiToCurrency(null, null,
                satoshi, Jni.getCPtr(amountFiat), currencyNum, Jni.getCPtr(error));
        return core.doublep_value(amountFiat);
    }

    /**
     * Convert a fiat value to satoshis.
     * @param amount the fiat amount to be converted to satoshis
     * @param currency the fiat code such as USD or EUR.
     * @return the amount of satoshis
     */
    public long currencyToSatoshi(double amount, String currency) {
        tABC_Error error = new tABC_Error();
        tABC_CC result;
        SWIGTYPE_p_int64_t satoshi = core.new_int64_tp();
        SWIGTYPE_p_long l = core.p64_t_to_long_ptr(satoshi);

        int currencyNum = Currencies.instance().map(currency);
        result = core.ABC_CurrencyToSatoshi(null, null,
            amount, currencyNum, satoshi, error);

        return Jni.get64BitLongAtPtr(Jni.getCPtr(l));
    }

}
