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
import co.airbitz.internal.SWIGTYPE_p_p_unsigned_char;
import co.airbitz.internal.SWIGTYPE_p_unsigned_int;
import co.airbitz.internal.core;
import co.airbitz.internal.coreConstants;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;
import co.airbitz.internal.tABC_PasswordRule;

/**
 *  AirbitzCore is the main entry point to interact with ABC. Before calling
 *  any methods, {@link #init init} has to be called. After that, you can begin
 *  {@link #createAccount creating accounts}, {@link #passwordLogin signing in}
 *  and {@link Account#data encrypting} your data.
 */
public class AirbitzCore {
    private static String TAG = AirbitzCore.class.getSimpleName();
    private static Object LOCK = new Object();

    /**
     * These are log levels that can be supplied when using {@link
     * AirbitzCore#log log}.  The values are implied when using {@link #logd
     * logd}, {@link #logi logi}, {@link #logw logw} and {@link #loge loge}.
     */
    public enum LogLevel {
        ERROR(0),
        WARNING(1),
        INFO(2),
        DEBUG(3);

        private final int value;
        LogLevel(int value) {
            this.value = value;
        }
    }

    private static AirbitzCore mInstance = null;
    private static boolean mInitialized = false;
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

    public static AirbitzCore getApi() {
        synchronized (LOCK) {
            if (mInstance == null) {
                mInstance = new AirbitzCore();
                mInstance.logi("New AirbitzCore");
            }
        }
        return mInstance;
    }

    /**
     * Initialize the AirbitzCore object. Required for functionality of ABC SDK.
     * @param airbitzApiKey API key obtained from Airbitz Inc.
     */
    public void init(Context context, String airbitzApiKey) {
        init(context, airbitzApiKey, null);
    }

    /**
     * Initialize the AirbitzCore object. Required for functionality of ABC SDK.
     * @param airbitzApiKey API key obtained from Airbitz Inc.
     * @param hiddenbitzKey (Optional) unique key used to encrypt private keys for use as implementation
     * specific "gift cards" that are only redeemable by applications using this implementation.
     */
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

   private static String seedData() {
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

    /**
     * Destroy the in memory user cache in the core. This is equivalent to
     * logging all users out.
     */
    public void destroy() {
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_ClearKeyCache(error);
        if (result != tABC_CC.ABC_CC_Ok) {
            AirbitzCore.loge(error.toString());
        }
    }

    private static final LogLevel MIN_LEVEL = LogLevel.INFO;

    /**
     * Log a message at a specified log level.
     * @param level of the log message (DEBUG, INFO, WARING, ERROR)
     * @param debugString to write to logs
     */
    public static void log(LogLevel level, String debugString) {
        if (level.value <= MIN_LEVEL.value) {
            core.ABC_Log(debugString);
        }
    }

    /**
     * Log a message at the error level.
     * @param debugString to write to logs
     */
    public static void loge(String debugString) {
        log(LogLevel.ERROR, debugString);
    }

    /**
     * Log a message at the warning level.
     * @param debugString to write to logs
     */
    public static void logw(String debugString) {
        log(LogLevel.WARNING, debugString);
    }

    /**
     * Log a message at the info level.
     * @param debugString to write to logs
     */
    public static void logi(String debugString) {
        log(LogLevel.INFO, debugString);
    }

    /**
     * Log a message at the debug level.
     * @param debugString to write to logs
     */
    public static void logd(String debugString) {
        log(LogLevel.DEBUG, debugString);
    }

    /**
     * Gets the version of AirbitzCore compiled into this implementation
     * @return version number
     */
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

    /*
     * Uploads debug log to Airbitz servers.
     */
    public boolean uploadLogs() {
        return uploadLogs(null, null);
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

        core.ABC_UploadLogs(username, password, Error);
        return Error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    /**
     * Call this routine when backgrounding your application
     */
    public void background() {
        for (Account account : mAccounts) {
            account.engine().stop();
        }
    }

    /**
     * Call this routine when foregrounding your application
     */
    public void foreground() {
        for (Account account : mAccounts) {
            account.engine().start();
        }
    }

    /**
     * Change the connectivity for the library. This will cause network
     * activity to stop or start if connectivity was lost or regained.
     * @param hasConnectivity true if it has connectivity, false otherwise
     */
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

    /**
     * Return whether the library has connectivity.
     * @return true has connectivity
     */
    public boolean hasConnectivity() {
        return mConnectivity;
    }


    /**
     * Determines if the core library was compiled for testnet or mainnet.
     * @return true if the library is on testnet
     */
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

    /**
     * Get the exchange cache system.
     * @return the exchange cache.
     */
    public ExchangeCache exchangeCache() {
        return mExchangeCache;
    }

    /**
     * Creates a QR encoded byte array from the given text.
     * @return byte array of encoded text
     */
    public byte[] qrEncode(String text) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_unsigned_char ppChar = core.longp_to_unsigned_ppChar(lp);

        SWIGTYPE_p_int pWidth = core.new_intp();
        SWIGTYPE_p_unsigned_int pUCount = core.int_to_uint(pWidth);

        core.ABC_QrEncode(text, ppChar, pUCount, error);
        int width = core.intp_value(pWidth);
        return Jni.getBytesAtPtr(core.longp_value(lp), width * width);
    }

    /**
     * Encodes a QR code byte array into Bitmap.
     * @param array byte array such as one produced by {@link
     * AirbitzCore#qrEncode}
     * @return qrcode
     */
    public Bitmap qrEncode(byte[] array) {
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
    public Bitmap qrEncode(byte[] array, int width, int scale) {
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

    /**
     * Get a list of supported currencies
     * @return list of supported currencies
     */
    public List<CoreCurrency> currencies() {
        return Currencies.instance().currencies();
    }

    /**
     * Get a list of supported bitcoin denominations
     * @return list of supported bitcoin denominations
     */
    public List<BitcoinDenomination> denominations() {
        return BitcoinDenomination.denominations();
    }

    /**
     * Get a list of exchanges sources
     * @return list of supported exchange sources
     */
    public List<String> exchangeRateSources() {
        List<String> sources = new ArrayList<>();
        sources.add("Bitstamp");
        sources.add("Bitfinex");
        sources.add("BitcoinAverage");
        sources.add("BraveNewCoin");
        sources.add("Coinbase");
        sources.add("CleverCoin");
        return sources;
    }

    /**
     * Get a list of previously logged in usernames on this device
     * @return list of previously logged in usernames
     */
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

    /**
     * Checks if an account with the specified username exists locally on the
     * current device.
     * @param username username of account to check
     * @return true if account exists locally
     */
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

    /**
     * Determines if an OTP reset is pending for an account
     * @param username username of account to check
     * @return true if the account has OTP pending
     */
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
            throw new AirbitzException(error.getCode(), error);
        }
        return false;
    }

    /**
     * Deletes named account from local device. Account is recoverable if it
     * contains a password.
     * @param username username of account to delete
     * @return true if the account was deleted
     */
    public boolean accountDeleteLocal(String username) {
        tABC_Error error = new tABC_Error();
        core.ABC_AccountDelete(username, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    /**
     * Transforms a username into the internal format used for hashing.  This
     * collapses spaces, converts to lowercase, and checks for invalid
     * characters.
     * @param username username to fix
     * @return fixed username with text lowercased, leading and trailing white
     * space removed, and all whitespace condensed to one space.
     */
    public String usernameFix(String username) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);
        core.ABC_FixUsername(ppChar, username, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            return Jni.getStringAtPtr(core.longp_value(lp));
        } else {
            throw new AirbitzException(error.getCode(), null);
        }
    }

    /**
     * Check on the server if a username is available
     * @param username the username to check
     * @return true if username is available
     */
    public boolean usernameAvailable(String username) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_AccountAvailable(username, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            return true;
        } else {
            throw new AirbitzException(error.getCode(), null);
        }
    }

    /**
     * Create an Airbitz account with specified username and password.
     * @param username the account username
     * @param password the account password
     * @return Account object
     */
    public Account createAccount(String username, String password) throws AirbitzException {
        return createAccount(username, password, null);
    }

    /**
     * Create an Airbitz account with specified username, password, and PIN.
     * @param username the account username
     * @param password the account password
     * @param pin the account PIN
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
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     * PasswordRulesCheck class contains information regarding the quality of a
     * password.  Information includes how long cracking a password will take,
     * as well as if the password passes the Airbitz password requirements.
     * Requiring that the user's password meets each of these criteria ensures
     * that their data will be safer.
     */
    public static class PasswordRulesCheck {
        // How many seconds (approximately it will take to crack this password brute-forcing
        public double secondsToCrack;

        // Indicates if the password is too short
        public boolean tooShort;

        // Indicates if the password is missing a number
        public boolean noNumber;

        // Indicates if the password is missing an uppercase letter
        public boolean noUpperCase;

        // Indicates if the password is missing an lowercase letter
        public boolean noLowerCase;

        // The minimum password length as recommended by Airbitz
        public int minPasswordLength;
    }

    /**
     * Calculate the number of seconds it would take to crack the given password.
     * @param password the password to check
     * @return seconds to crack the password
     */
    public PasswordRulesCheck passwordRulesCheck(String password) {
        PasswordRulesCheck check = new PasswordRulesCheck();
        check.minPasswordLength = coreConstants.ABC_MIN_PIN_LENGTH;

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

        check.secondsToCrack = core.doublep_value(seconds);

        int count = core.intp_value(pCount);
        long base = core.longp_value(lp);
        for (int i = 0; i < count; i++) {
            Jni.pLong temp = new Jni.pLong(base + i * 4);
            long start = core.longp_value(temp);
            tABC_PasswordRule rule = Jni.newPasswordRule(start);
            if (rule.getSzDescription().contains("Must have at least one upper case letter")) {
                check.noUpperCase = !rule.getBPassed();
            } else if (rule.getSzDescription().contains("Must have at least one lower case letter")) {
                check.noLowerCase = !rule.getBPassed();
            } else if (rule.getSzDescription().contains("Must have at least one number")) {
                check.noNumber = !rule.getBPassed();
            } else if (rule.getSzDescription().contains("Must have at least")) {
                check.tooShort = !rule.getBPassed();
            }
        }
        return check;
    }

    /**
     * Checks if this account has a password set. Accounts without passwords
     * cannot be logged into from a different device.
     * @param username an account username
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
     * @param username an account username
     * @param password the password for the account
     * @param otpToken the OTP token, if applicable
     * @return Account object of signed in user
     */
    public Account passwordLogin(String username, String password, String otpToken) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        if (otpToken != null) {
            otpKeySet(username, otpToken);
        }
        SWIGTYPE_p_long pToken = core.new_longp();
        SWIGTYPE_p_p_char ppToken = core.longp_to_ppChar(pToken);

        SWIGTYPE_p_long pTokenDate = core.new_longp();
        SWIGTYPE_p_p_char ppDate = core.longp_to_ppChar(pTokenDate);

        core.ABC_PasswordLogin(username, password, ppToken, ppDate, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            AirbitzException exception = new AirbitzException(error.getCode(), error);
            exception.mOtpResetToken = Jni.getStringAtPtr(core.longp_value(pToken));
            exception.mOtpResetDate = Jni.getStringAtPtr(core.longp_value(pTokenDate));
            throw exception;
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
     * @param username an account username
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
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     * Checks whether a user has recovery as an option for login
     * @param username an account username
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
     * @param username the account username
     * @param answers an array of recovery question answers
     * @param otpToken the OTP token, if applicable
     * @return Account object of signed in user
     */
    public Account recoveryLogin(String username, String[] answers, String otpToken) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        if (otpToken != null) {
            otpKeySet(username, otpToken);
        }

        SWIGTYPE_p_long pToken = core.new_longp();
        SWIGTYPE_p_p_char ppToken = core.longp_to_ppChar(pToken);

        SWIGTYPE_p_long pTokenDate = core.new_longp();
        SWIGTYPE_p_p_char ppDate = core.longp_to_ppChar(pTokenDate);

        core.ABC_RecoveryLogin(username,
                Utils.arrayToString(answers), ppToken, ppDate, error);
        if (tABC_CC.ABC_CC_Ok != error.getCode()) {
            AirbitzException exception = new AirbitzException(error.getCode(), error);
            exception.mOtpResetToken = Jni.getStringAtPtr(core.longp_value(pToken));
            exception.mOtpResetDate = Jni.getStringAtPtr(core.longp_value(pTokenDate));
            throw exception;
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
            throw new AirbitzException(error.getCode(), error);
        }
        if (otpToken != null) {
            otpKeySet(username, otpToken);
        }
        SWIGTYPE_p_int pWaitSeconds = core.new_intp();

        tABC_Error error = new tABC_Error();
        core.ABC_PinLogin(username, pin, pWaitSeconds, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            AirbitzException exception = new AirbitzException(error.getCode(), error);
            exception.mWaitSeconds = core.intp_value(pWaitSeconds);
            throw exception;
        }
        Account account = new Account(this, username, null);
        mAccounts.add(account);
        return account;
    }

    /**
    * Launches an OTP reset timer on the server, which will disable the OTP
    * authentication requirement when it expires.
    * @param username
    * @param token Reset token returned by the passwordLogin... routines if sign in
    * fails due to missing or incorrect OTP.
    */
    public void otpResetRequest(String username, String token) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpResetSet(username, token, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
    * Parses a bitcoin BIP21 URI, WIF private key, or Airbitz hbits private key
    * @param text to parse
    * @return ParsedUri object
    */
    public ParsedUri parseUri(String text) throws AirbitzException {
        return new ParsedUri(text);
    }

    private void otpKeySet(String username, String secret) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpKeySet(username, secret, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }
}
