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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Build;

import java.io.File;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_bool;
import co.airbitz.internal.SWIGTYPE_p_double;
import co.airbitz.internal.SWIGTYPE_p_int;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_p_sABC_PasswordRule;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_QuestionChoices;
import co.airbitz.internal.SWIGTYPE_p_unsigned_int;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

public class AirbitzCore {
    private static String TAG = AirbitzCore.class.getSimpleName();

    private static Object LOCK = new Object();

    public enum LogLevel {
        ERROR(0),
        WARNING(1),
        INFO(2),
        DEBUG(3);

        private final int value;
        LogLevel(int value) {
            this.value= value;
        }
    }

    private static AirbitzCore mInstance = null;
    private static boolean mInitialized = false;
    private static Context mContext;
    private List<Account> mAccounts;
    private boolean mConnectivity = true;
    private ExchangeCache mExchangeCache;

    static {
        System.loadLibrary("abc");
        System.loadLibrary("airbitz");
    }

    private AirbitzCore() {
        mAccounts = new ArrayList<Account>();
        mExchangeCache = new ExchangeCache();
    }

    public static AirbitzCore getApi(Context context) {
        mContext = context;
        synchronized (LOCK) {
            if (mInstance == null) {
                mInstance = new AirbitzCore();
                mInstance.logi("New AirbitzCore");
            }
        }
        return mInstance;
    }

    public static AirbitzCore getApi() {
        synchronized (LOCK) {
            if (mInstance == null) {
                mInstance = new AirbitzCore();
                mInstance.logi("New AirbitzCore");
            }
        }
        return mInstance;
    }

    protected Context getContext() {
        return mContext;
    }

    public void init(Context context, String airbitzApiKey, String hiddenbitzKey) {
        if (mInitialized) {
            return;
        }
        String seed = seedData();
        tABC_Error error = new tABC_Error();
        File filesDir = context.getFilesDir();
        String certPath = Utils.setupCerts(context, filesDir);
        core.ABC_Initialize(filesDir.getPath(), certPath, airbitzApiKey, hiddenbitzKey, seed, seed.length(), error);
        mInitialized = true;

        // Fetch General Info
        new Thread(new Runnable() {
            public void run() {
                generalInfoUpdate();
            }
        }).start();
    }

   public static String seedData() {
        String strSeed = "";

        long time = System.nanoTime();
        ByteBuffer bb1 = ByteBuffer.allocate(8);
        bb1.putLong(time);
        strSeed += bb1.array();

        Random r = new SecureRandom();
        ByteBuffer bb2 = ByteBuffer.allocate(4);
        bb2.putInt(r.nextInt());
        strSeed += bb2.array();

        return strSeed;
    }

    boolean generalInfoUpdate() {
        tABC_Error error = new tABC_Error();
        core.ABC_GeneralInfoUpdate(error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    public void destroy() {
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_ClearKeyCache(error);
        if (result != tABC_CC.ABC_CC_Ok) {
            AirbitzCore.loge(error.toString());
        }
    }

    private static final LogLevel MIN_LEVEL = LogLevel.INFO;
    public static void log(LogLevel level, String debugString) {
        if (level.value <= MIN_LEVEL.value) {
            core.ABC_Log(debugString);
        }
    }

    public static void loge(String debugString) {
        log(LogLevel.ERROR, debugString);
    }

    public static void logw(String debugString) {
        log(LogLevel.WARNING, debugString);
    }

    public static void logi(String debugString) {
        log(LogLevel.INFO, debugString);
    }

    public static void logd(String debugString) {
        log(LogLevel.DEBUG, debugString);
    }

    public String version() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);
        core.ABC_Version(ppChar, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            return Jni.getStringAtPtr(core.longp_value(lp));
        }
        return "";
    }

    public boolean uploadLogs() {
        return uploadLogs(null, null);
    }

    public boolean uploadLogs(String username, String password) {
        tABC_Error Error = new tABC_Error();

        // Send system information to end of logfile
        String deviceName = Build.MODEL;
        String deviceMan = Build.MANUFACTURER;
        String deviceBrand = Build.BRAND;
        String deviceOS = Build.VERSION.RELEASE;

        AirbitzCore.logi("Platform:" + deviceBrand + " " + deviceMan + " " + deviceName);
        AirbitzCore.logi("Android Version:" + deviceOS);

        core.ABC_UploadLogs(username, password, Error);
        return Error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    public void background() {
        for (Account account : mAccounts) {
            account.engine().stop();
        }
    }

    public void foreground() {
        for (Account account : mAccounts) {
            account.engine().start();
        }
    }

    public void connectivity(boolean hasConnectivity) {
        mConnectivity = hasConnectivity;
        if (hasConnectivity) {
            restoreConnectivity();
        } else {
            lostConnectivity();
        }
    }

    private void restoreConnectivity() {
        synchronized (mAccounts) {
            for (Account account : mAccounts) {
                account.restoreConnectivity();
            }
        }
    }

    private void lostConnectivity() {
        synchronized (mAccounts) {
            for (Account account : mAccounts) {
                account.lostConnectivity();
            }
        }
    }

    public boolean hasConnectivity() {
        return mConnectivity;
    }


    public boolean isTestNet() {
        tABC_CC result;
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool istestnet = Jni.newBool(Jni.getCPtr(lp));

        result = core.ABC_IsTestNet(istestnet, error);

        if(result.equals(tABC_CC.ABC_CC_Ok)) {
            return Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        } else {
            AirbitzCore.logi("isTestNet error:"+error.getSzDescription());
        }
        return false;
    }

    public ExchangeCache exchangeCache() {
        return mExchangeCache;
    }

    public Bitmap qrEncode(byte[] array) {
        return qrEncode(array, (int) Math.sqrt(array.length), 16);
    }

    public Bitmap qrEncode(byte[] bits, int width, int scale) {
        Bitmap bmpBinary = Bitmap.createBitmap(width*scale, width*scale, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < width; y++) {
                bmpBinary.setPixel(x, y, bits[y * width + x] != 0 ? Color.BLACK : Color.WHITE);
            }
        }
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        Bitmap resizedBitmap = Bitmap.createBitmap(bmpBinary, 0, 0, width, width, matrix, false);
        return resizedBitmap;
    }

    public List<String> exchangeRateSources() {
        List<String> sources = new ArrayList<>();
        sources.add("Bitstamp");
        sources.add("BraveNewCoin");
        sources.add("Coinbase");
        sources.add("CleverCoin");
        return sources;
    }

    public List<String> accountListLocal() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);
        core.ABC_ListAccounts(ppChar, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            List<String> array = Arrays.asList(Jni.getStringAtPtr(core.longp_value(lp)).split("\\n"));
            List<String> list = new ArrayList<String>();
            for (int i=0; i< array.size(); i++) {
                if(!array.get(i).isEmpty()) {
                    list.add(array.get(i));
                }
            }
            return list;
        }
        return null;
    }

    public boolean accountSyncExistsLocal(String username) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool exists = Jni.newBool(Jni.getCPtr(lp));
        core.ABC_AccountSyncExists(username, exists, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            return Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        }
        return false;
    }

    public boolean isOtpResetPending(String username) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);
        core.ABC_OtpResetGet(ppChar, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            String userNames = Jni.getStringAtPtr(core.longp_value(lp));
            if (userNames != null && username != null) {
                return userNames.contains(username);
            }
        } else {
            throw new AirbitzException(mContext, error.getCode(), error);
        }
        return false;
    }

    public boolean accountDeleteLocal(String account) {
        tABC_Error error = new tABC_Error();
        core.ABC_AccountDelete(account, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    public String usernameAvailable(String account) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_AccountAvailable(account, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            return null;
        } else {
            throw new AirbitzException(mContext, error.getCode(), null);
        }
    }

    /**
     * Create an Airbitz account with specified username and password.
     * @param username
     * @param password
     * @return Account* Account object
     */
    public Account createAccount(String username, String password) throws AirbitzException {
        return createAccount(username, password, null);
    }

    /**
     * Create an Airbitz account with specified username, password, and PIN.
     * @param username
     * @param password
     * @param pin
     * @return Account* Account object
     */
    public Account createAccount(String username, String password, String pin) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_CreateAccount(username, password, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            Account account = new Account(this, username, password);
            if (null != pin) {
                account.pin(pin);
            }
            mAccounts.add(account);
            return account;
        } else {
            throw new AirbitzException(null, error.getCode(), error);
        }
    }

    /**
     * Calculate the number of seconds it would take to crack the given password.
     * @param password
     * @return seconds to crack the password
     */
    public double passwordSecondsToCrack(String password) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_double seconds = core.new_doublep();
        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int puCount = core.int_to_uint(pCount);
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_sABC_PasswordRule pppRules = core.longp_to_pppPasswordRule(lp);

        core.ABC_CheckPassword(password, seconds, pppRules, puCount, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            AirbitzCore.loge("Error in passwordSecondsToCrack:  " + error.getSzDescription());
            return 0;
        }
        return core.doublep_value(seconds);
    }

    /**
     * Checks a password for valid entropy looking for correct minimum
     * requirements such as upper, lowercase letters, numbers, and # of digits.
     * This should be used by app to give feedback to user before creating a
     * new account.
     * @param password password to check
     */
    public List<PasswordRule> passwordRules(String password) {
        List<PasswordRule> list = new ArrayList<PasswordRule>();
        boolean bNewPasswordFieldsAreValid = true;

        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_double seconds = core.new_doublep();
        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int puCount = core.int_to_uint(pCount);
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_sABC_PasswordRule pppRules = core.longp_to_pppPasswordRule(lp);

        core.ABC_CheckPassword(password, seconds, pppRules, puCount, error);

        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            AirbitzCore.loge("Error in PasswordRule:  " + error.getSzDescription());
            return null;
        }

        int count = core.intp_value(pCount);
        long base = core.longp_value(lp);
        for (int i = 0; i < count; i++) {
            Jni.pLong temp = new Jni.pLong(base + i * 4);
            long start = core.longp_value(temp);
            PasswordRule pRule = new PasswordRule(start, false);
            list.add(pRule);
        }
        return list;
    }

    /**
     * Checks if this account has a password set. Accounts without passwords
     * cannot be logged into from a different device.
     * @param username
     * @return true is the account has a password set
     */
    public boolean accountHasPassword(String username) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool exists = Jni.newBool(Jni.getCPtr(lp));
        core.ABC_PasswordExists(username, exists, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            return Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        }
        return false;
    }

    /**
     * Login into an account with a password.
     * @param username
     * @param password
     * @param otpToken
     * @return Account object of signed in user
     */
    public Account passwordLogin(String username, String password, String otpToken) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        if (otpToken != null) {
            otpKeySet(username, otpToken);
        }
        core.ABC_SignIn(username, password, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mContext, error.getCode(), error);
        }
        Account account = new Account(this, username, password);
        mAccounts.add(account);
        return account;
    }

    /**
     * Fetch the possible choices for recovery questions.
     * @return an array of QuestionChoice
     */
    public QuestionChoice[] recoveryQuestionChoices() {
        tABC_Error error = new tABC_Error();
        QuestionChoice[] mChoices = null;
        SWIGTYPE_p_long plong = core.new_longp();
        SWIGTYPE_p_p_sABC_QuestionChoices ppQuestionChoices = core.longp_to_ppQuestionChoices(plong);

        core.ABC_GetQuestionChoices(ppQuestionChoices, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            long lp = core.longp_value(plong);
            QuestionChoices qcs = new QuestionChoices(lp);
            mChoices = qcs.getChoices();
        }
        return mChoices;
    }

    /**
     * Fetch the recovery questions for a user.
     * @param username
     * @return new line delimited string of recovery questions
     */
    public String recoveryQuestions(String username) throws AirbitzException {
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        tABC_CC result = core.ABC_GetRecoveryQuestions(username, ppChar, error);
        String questionString = Jni.getStringAtPtr(core.longp_value(lp));
        if (result == tABC_CC.ABC_CC_Ok) {
            return questionString;
        } else {
            throw new AirbitzException(null, error.getCode(), error);
        }
    }

    /**
     * Checks whether a user has recovery as an option for login
     * @param username
     * @return true if there are recovery questions for this user
     */
    public boolean accountHasRecovery(String username) {
        try {
            String qstring = recoveryQuestions(username);
            if (qstring != null) {
                String[] qs = qstring.split("\n");
                if (qs.length > 1) {
                    // Recovery questions set
                    return true;
                }
            }
        } catch (AirbitzException e) {
            AirbitzCore.loge("hasRecoveryQuestionsSet error:");
        }
        return false;
    }

    /**
     * Login using recovery questions rather than a password
     * @param username
     * @param answers
     * @param otpToken
     * @return Account object of signed in user
     */
    public Account recoveryLogin(String username, String[] answers, String otpToken) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        if (otpToken != null) {
            otpKeySet(username, otpToken);
        }
        core.ABC_RecoveryLogin(username,
                Utils.arrayToString(answers), error);
        if (tABC_CC.ABC_CC_Ok != error.getCode()) {
            throw new AirbitzException(mContext, error.getCode(), error);
        }
        Account account = new Account(this, username, null);
        mAccounts.add(account);
        return account;
    }

    /**
     * Checks if this account has a pin set
     * @param username
     * @return true if a pin is set
     */
    public boolean accountHasPin(String username) {
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool exists = Jni.newBool(Jni.getCPtr(lp));

        core.ABC_PinLoginExists(username, exists, error);

        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            return Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        } else {
            AirbitzCore.loge("PinLoginExists error:"+error.getSzDescription());
            return false;
        }
    }

    /**
     * Login using a pin. This only works if the user's data already exists
     * locally (from a password or recovery login).
     * @param username
     * @param pin
     * @param otpToken
     * @return Account object of signed in user
     */
    public Account pinLogin(String username, String pin, String otpToken) throws AirbitzException {
        if (username == null || pin == null) {
            tABC_Error error = new tABC_Error();
            error.setCode(tABC_CC.ABC_CC_Error);
            throw new AirbitzException(mContext, error.getCode(), error);
        }
        if (otpToken != null) {
            otpKeySet(username, otpToken);
        }
        tABC_Error error = new tABC_Error();
        core.ABC_PinLogin(username, pin, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mContext, error.getCode(), error);
        }
        Account account = new Account(this, username, null);
        mAccounts.add(account);
        return account;
    }

    public void otpResetRequest(String username) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpResetSet(username, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mContext, error.getCode(), error);
        }
    }

    private void otpKeySet(String username, String secret) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpKeySet(username, secret, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mContext, error.getCode(), error);
        }
    }
}
