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

import android.content.Context;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

/**
 * Utils providers various functions for working with fiat values and BTC
 * values as well as converting values from BTC strings to satoshis.
 */
public class Utils {
    private static final String CERT_FILENAME = "ca-certificates.crt";

    static String setupCerts(Context context, File filesDir) {
        List<String> files = Arrays.asList(filesDir.list());
        OutputStream outputStream = null;
        if (!files.contains(CERT_FILENAME)) {
            InputStream certStream = context.getResources().openRawResource(R.raw.ca_certificates);
            try {
                outputStream = context.openFileOutput(CERT_FILENAME, Context.MODE_PRIVATE);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            copyStreamToFile(certStream, outputStream);
        }
        return filesDir.getPath() + "/" + CERT_FILENAME;
    }
    static void copyStreamToFile(InputStream src, OutputStream outputStream) {
        final byte[] largeBuffer = new byte[1024 * 4];
        int bytesRead;

        try {
            while ((bytesRead = src.read(largeBuffer)) > 0) {
                if (largeBuffer.length == bytesRead) {
                    outputStream.write(largeBuffer);
                } else {
                    final byte[] shortBuffer = new byte[bytesRead];
                    System.arraycopy(largeBuffer, 0, shortBuffer, 0, bytesRead);
                    outputStream.write(shortBuffer);
                }
            }
            outputStream.flush();
            outputStream.close();
            src.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void printABCError(tABC_Error pError) {
        if (pError.getCode() != tABC_CC.ABC_CC_Ok) {
            AirbitzCore.loge(
                String.format("Code: %s, Desc: %s, Func: %s, File: %s, Line: %d\n",
                    pError.getCode().toString(),
                    pError.getSzDescription(),
                    pError.getSzSourceFunc(),
                    pError.getSzSourceFile(),
                    pError.getNSourceLine()));
        }
    }

    static String userBtcSymbol(Account account) {
        Settings settings = account.settings();
        if (settings == null) {
            return "";
        }
        BitcoinDenomination bitcoinDenomination =
            settings.bitcoinDenomination();
        if (bitcoinDenomination == null) {
            AirbitzCore.logw("Bad bitcoin denomination from core settings");
            return "";
        }
        return bitcoinDenomination.btcSymbol();
    }

    static int userDecimalPlaces(Account account) {
        int decimalPlaces = 8; // for BitcoinDenomination.BTC
        Settings settings = account.settings();
        if (settings == null) {
            return 2;
        }
        BitcoinDenomination bitcoinDenomination =
            settings.bitcoinDenomination();
        if (bitcoinDenomination != null) {
            int label = bitcoinDenomination.type();
            if (label == BitcoinDenomination.UBTC)
                decimalPlaces = 2;
            else if (label == BitcoinDenomination.MBTC)
                decimalPlaces = 5;
        }
        return decimalPlaces;
    }

    static int userDecimalPlaces(int multiplier) {
        int decimalPlaces = 8; // for BitcoinDenomination.BTC_MULTIPLIER
        if (multiplier == BitcoinDenomination.UBTC_MULTIPLIER)
            decimalPlaces = 2;
        else if (multiplier == BitcoinDenomination.MBTC_MULTIPLIER)
            decimalPlaces = 5;
        return decimalPlaces;
    }

    static String arrayToString(String[] arr) {
        StringBuffer buf = new StringBuffer("");
        for (String s : arr) {
            buf.append(s).append("\n");
        }
        return buf.toString();
    }

    /**
     * Utility function to format a fiat currency.
     * @param value the fiat value
     * @param currency the currency code i.e. USD or EUR
     * @param withSymbol include the currency symbol when formatting
     * @return a formatted fiat string
     */
    public static String formatCurrency(double value, String currency, boolean withSymbol) {
        return formatCurrency(value, currency, withSymbol, 2);
    }

    /**
     * Utility function to format a fiat currency.
     * @param value the fiat value
     * @param currency the currency code i.e. USD or EUR
     * @param withSymbol include the currency symbol when formatting
     * @param decimalPlaces the number of decimal places to include
     * @return a formatted fiat string
     */
    public static String formatCurrency(double value, String currency, boolean withSymbol, int decimalPlaces) {
        String pre;
        String denom = Currencies.instance().lookup(currency).symbol + " ";
        if (value < 0) {
            value = Math.abs(value);
            pre = withSymbol ? "-" + denom : "-";
        } else {
            pre = withSymbol ? denom : "";
        }
        BigDecimal bd = new BigDecimal(value);
        DecimalFormat df;
        switch(decimalPlaces) {
        case 3:
            df = new DecimalFormat("#,##0.000", new DecimalFormatSymbols(Locale.getDefault()));
            break;
        default:
            df = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.getDefault()));
            break;
        }
        return pre + df.format(bd.doubleValue());
    }

    /**
     * Utility function to format a BTC value. This includes the account's
     * preferred denomination as is set in {@link Settings#bitcoinDenomination}.
     * @param account the user the value is being formatted for
     * @param amount the amount in satoshis
     * @return a formatted BTC string
     */
    public static String formatSatoshi(Account account, long amount) {
        return formatSatoshi(account, amount, true);
    }

    /**
     * Utility function to format a BTC value. This includes the account's
     * preferred denomination as is set in {@link Settings#bitcoinDenomination}.
     * @param account the user the value is being formatted for
     * @param amount the amount in satoshis
     * @param withSymbol include the BTC symbol in the result
     * @return a formatted BTC string
     */
    public static String formatSatoshi(Account account, long amount, boolean withSymbol) {
        return formatSatoshi(account, amount, withSymbol, Utils.userDecimalPlaces(account));
    }

    /**
     * Utility function to format a BTC value. This includes the account's
     * preferred denomination as is set in {@link Settings#bitcoinDenomination}.
     * @param account the user the value is being formatted for
     * @param amount the amount in satoshis
     * @param withSymbol include the BTC symbol in the result
     * @param decimalPlaces the number of decimal places to includes
     * @return a formatted BTC string
     */
    public static String formatSatoshi(Account account, long amount, boolean withSymbol, int decimalPlaces) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        int dp = Utils.userDecimalPlaces(account);

        boolean negative = amount < 0;
        if(negative)
            amount = -amount;
        int result = Jni.FormatAmount(amount, Jni.getCPtr(ppChar), dp, false, Jni.getCPtr(error));
        if (result != 0) {
            return "";
        } else {
            dp = decimalPlaces > -1 ? decimalPlaces : dp;
            String pretext = "";
            if (negative) {
                pretext += "-";
            }
            if (withSymbol) {
                pretext += Utils.userBtcSymbol(account);
            }

            BigDecimal bd = new BigDecimal(amount);
            bd = bd.movePointLeft(decimalPlaces);

            DecimalFormat df =
                new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.getDefault()));
            if (decimalPlaces == 5) {
                df = new DecimalFormat("#,##0.#####", new DecimalFormatSymbols(Locale.getDefault()));
            } else if(decimalPlaces == 8) {
                df = new DecimalFormat("#,##0.########", new DecimalFormatSymbols(Locale.getDefault()));
            }
            return pretext + df.format(bd.doubleValue());
        }
    }

    /**
     * Utility function to convert a BTC string to satoshi's.  This uses the
     * preferred denomination as is set in {@link Settings#bitcoinDenomination}.
     * @param account the user the value is being parsed for
     * @param amount the BTC string in the user's preferred denomination
     * @return the number of satoshis
     */
    public static long btcStringToSatoshi(Account account, String amount) {
        int decimalPlaces = Utils.userDecimalPlaces(account);
        try {
            // Parse using the current locale
            Number cleanAmount =
                new DecimalFormat().parse(amount, new ParsePosition(0));
            if (null == cleanAmount) {
                return 0L;
            }
            // Convert to BD so we don't lose precision
            BigDecimal bd = BigDecimal.valueOf(cleanAmount.doubleValue());
            DecimalFormat df = new DecimalFormat("###0.############", new DecimalFormatSymbols(Locale.getDefault()));
            String bdstr = df.format(bd.doubleValue());
            long parseamt = Jni.ParseAmount(bdstr, decimalPlaces);
            long max = Math.max(parseamt, 0);
            return max;
        } catch (Exception e) {
            // Shhhhh
        }
        return 0L;
    }
}
