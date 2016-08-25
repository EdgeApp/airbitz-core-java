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

package co.airbitz.core.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Build;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import co.airbitz.core.AirbitzCore;
import co.airbitz.core.BuildConfig;
import co.airbitz.core.R;
import co.airbitz.internal.tABC_Error;

public class AndroidUtils {
    private static final String CERT_FILENAME = "ca-certificates.crt";

    public static AirbitzCore init(Context context, String airbitzApiKey, String type, String hiddenbitzKey) {
        AirbitzCore api = AirbitzCore.getApi();
        File filesDir = context.getFilesDir();
        File certPath = setupCertificates(context, filesDir);
        api.init(filesDir, certPath, airbitzApiKey, type, hiddenbitzKey);
        return api;
    }

    static File setupCertificates(Context context, File filesDir) {
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
        return new File(filesDir.getPath(), CERT_FILENAME);
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

    /**
     * Encodes a QR code byte array into Bitmap.
     * @param array byte array such as one produced by {@link
     * AirbitzCore#qrEncode}
     * @return qrcode
     */
    public static Bitmap qrEncode(byte[] array) {
        return qrEncode(array, (int) Math.sqrt(array.length), 16);
    }

    /**
     * Encodes a QR code byte array into Bitmap
     * @param array byte array such as one produced by {@link
     * AirbitzCore#qrEncode}
     * @param width width of the bitmap
     * @param scale scale of the bitmap
     * @return qrcode
     */
    public static Bitmap qrEncode(byte[] array, int width, int scale) {
        Bitmap bmpBinary = Bitmap.createBitmap(width*scale, width*scale, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < width; y++) {
                bmpBinary.setPixel(x, y, array[y * width + x] != 0 ? Color.BLACK : Color.WHITE);
            }
        }
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        Bitmap resizedBitmap = Bitmap.createBitmap(bmpBinary, 0, 0, width, width, matrix, false);
        return resizedBitmap;
    }

    /*
     * Uploads debug log to Airbitz servers.
     * @param text to send to support staff
     */
    public boolean uploadLogs(String username, String password) {
        tABC_Error Error = new tABC_Error();

        // Send system information to end of logfile
        String deviceName = Build.MODEL;
        String deviceMan = Build.MANUFACTURER;
        String deviceBrand = Build.BRAND;
        String deviceOS = Build.VERSION.RELEASE;

        AirbitzCore.logi("Platform:" + deviceBrand + " " + deviceMan + " " + deviceName);
        AirbitzCore.logi("Android Version:" + deviceOS);
        return AirbitzCore.getApi().uploadLogs(username, password);
    }

    /**
     * Gets the version of AirbitzCore.
     * @return version number
     */
    public static String version() {
        return BuildConfig.VERSION_NAME;
    }
}
