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
import java.util.Arrays;
import java.util.List;

import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

class Utils {
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
            AirbitzCore.debugLevel(1,
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
            AirbitzCore.debugLevel(1, "Bad bitcoin denomination from core settings");
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
            int label = bitcoinDenomination.getDenominationType();
            if (label == BitcoinDenomination.UBTC)
                decimalPlaces = 2;
            else if (label == BitcoinDenomination.MBTC)
                decimalPlaces = 5;
        }
        return decimalPlaces;
    }
}
