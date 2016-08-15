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

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_bool;
import co.airbitz.internal.SWIGTYPE_p_double;
import co.airbitz.internal.SWIGTYPE_p_int64_t;
import co.airbitz.internal.SWIGTYPE_p_int;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_unsigned_char;
import co.airbitz.internal.SWIGTYPE_p_unsigned_int;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_AsyncBitCoinInfo;
import co.airbitz.internal.tABC_AsyncEventType;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

/**
 * The Account object represents a fully logged in account. This is returned by
 * various sign in  routines from {@link AirbitzCore}. It contains a {@link
 * Settings} object which are account settings that carry over from device to
 * device. Account also contains an array of {@link Wallet} object wallets and
 * archived wallets which should be checked for the {@link Wallet#isSynced
 * isSynced} before being accessed.<br><br>
 *
 * The {@link DataStore} object allows reading/writing of encrypted and backed
 * up key/value data to the user's account. This data is accessible from any
 * device that the user authenticates into using an app running on the Airbitz
 * SDK.
 */
public class Account {
    private static String TAG = Account.class.getSimpleName();

    public static int OTP_RESET_DELAY_SECS = 60 * 60 * 24 * 7;

    private String mUsername;
    private String mPassword;
    private AirbitzCore mApi;
    private Categories mCategories;
    private boolean mLoggedIn;
    private List<Wallet> mCachedWallets;
    Engine mEngine;
    Settings mSettings;

    /**
     * Account callbacks are used to handle asynchronous events from the core
     * for the current user.  This can include when data changes remotely or
     * when new bitcoin is received.
     */
    public interface Callbacks {
        /**
         * Called when the password changed remotely for this user.
         */
        public void remotePasswordChange();

        /**
         * Called when the user has been completed logged out.
         */
        public void loggedOut();

        /**
         * Called when the account has changed remotely. This includes a change
         * in settings which occurred on a different device.
         */
        public void accountChanged();

        /**
         * Called when wallets have begun loading. This can be used to display
         * a notification to the user that the wallet meta-data is still being
         * fetched or cloned.
         */
        public void walletsLoading();

        /**
         * Called when all the wallets have been loaded.
         */
        public void walletsLoaded();

        /**
         * Called when the data in a wallet changed remotely.
         */
        public void walletsChanged();

        /**
         * Called when the data in a wallet changed remotely.
         */
        public void walletChanged(Wallet wallet);

        /**
         * If the user has OTP enabled, this is called when a clock skew is
         * detected.
         */
        public void otpSkew();

        /**
         * Called when OTP has been enabled remotely.
         */
        public void otpRequired();

        /**
         * Called when OTP reset has been triggered.
         */
        public void otpResetPending();

        /**
         * Called when an exchange rate changes.
         */
        public void exchangeRateChanged();

        /**
         * Called when the block height changes.
         */
        public void blockHeightChanged();

        /**
         * Called when a wallet balance changes.
         */
        public void balanceUpdate(Wallet wallet, Transaction tx);

        /**
         * Called when new bitcoin has been received.
         */
        public void incomingBitcoin(Wallet wallet, Transaction tx);

        /**
         * Called when a private key sweep finishes.
         */
        public void sweep(Wallet wallet, Transaction tx, long amountSwept);
    }
    Callbacks mCallbacks;

    Account(AirbitzCore api, String username, String password) {
        mApi = api;
        mUsername = username;
        mPassword = password;
        mLoggedIn = true;
        mCategories = new Categories(this);
        mEngine = new Engine(api, this);
        settings();
    }

    Engine engine() {
        return mEngine;
    }

    /**
     * Define callbacks that will handle asynchronous events.
     */
    public void callbacks(Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /**
     * @return the accounts username
     */
    public String username() {
        return mUsername;
    }

    protected String password() {
        return mPassword;
    }

    /**
     * This indicates if the user logged in using a password, or some other
     * technique such as PIN or recovery answers.
     * @return true if the user logged in with a password
     */
    public boolean wasPasswordLogin() {
        return mPassword != null;
    }

    /**
     * This indicates if the user is logged in.
     * @return true if the user is logged in
     */
    public boolean isLoggedIn() {
        return mLoggedIn;
    }

    /**
     * This provides access to the account's category manager.
     * @return the category manager
     */
    public Categories categories() {
        return mCategories;
    }

    /**
     * This provides access to the account's DataStore manager.
     * @return the DataStore manager
     */
    public DataStore data(String storeId) {
        return new DataStore(this, storeId);
    }

    /**
     * This provides access to the account's settings.
     * @return the settings
     */
    public Settings settings() {
        if (mSettings != null) {
            return mSettings;
        }
        try {
            mSettings = new Settings(this).load();
            return mSettings;
        } catch (AirbitzException e) {
            AirbitzCore.loge("settings error:");
            return null;
        }
    }

    /**
     * Check if the input password matches the account password.
     * @param password the password to test
     * @return true if the password is correct
     */
    public boolean checkPassword(String password) {
        boolean check = false;
        if (password == null || password.isEmpty()) {
            check = !passwordExists();
        } else {
            tABC_Error error = new tABC_Error();
            SWIGTYPE_p_long lp = core.new_longp();
            SWIGTYPE_p_bool okay = Jni.newBool(Jni.getCPtr(lp));

            core.ABC_PasswordOk(mUsername, password, okay, error);
            if (error.getCode() == tABC_CC.ABC_CC_Ok) {
                check = Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
            } else {
                AirbitzCore.loge("Password OK error:"+ error.getSzDescription());
            }
        }
        return check;
    }

    /**
     * Check if a password exists for this account.
     * @return true if the account has a password set
     */
    public boolean passwordExists() {
        tABC_Error pError = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool exists = Jni.newBool(Jni.getCPtr(lp));

        tABC_CC result = core.ABC_PasswordExists(mUsername, exists, pError);
        if(pError.getCode().equals(tABC_CC.ABC_CC_Ok)) {
            return Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        } else {
            AirbitzCore.loge("Password Exists error:"+pError.getSzDescription());
            return true;
        }
    }

    String pin() throws AirbitzException {
        return settings().settings().getSzPIN();
    }

    /**
     * Setup the account PIN. If the account settings allow PIN login, then
     * this will setup the account PIN package as well.
     */
    public void pin(String pin) throws AirbitzException {
        Settings settings = settings();
        settings.settings().setSzPIN(pin);
        settings.save();

        tABC_Error error = new tABC_Error();
        core.ABC_PinSetup(username(), password(), pin, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     * Check the input against the account's PIN.
     * @param pin the PIN to check
     * @return true if the PIN matches the account PIN
     */
    public boolean checkPin(String pin) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool result = Jni.newBool(Jni.getCPtr(lp));
        core.ABC_PinCheck(mUsername, mPassword, pin, result, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            return Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        }
        return false;
    }

    /**
     * Checks whether the account has a pin.
     * @return true if the account has a pin
     */
    public boolean hasPinLogin() {
        return mApi.accountHasPinLogin(username());
    }

    /**
     * Setup the account PIN. If the account settings allow PIN login, then
     * this will setup the account PIN package as well.
     */
    public void pinLoginSetup(boolean enabled) throws AirbitzException {
        if (enabled) {
            pinLoginSetup();
        } else {
            pinLoginDisable();
        }
    }

    /**
     * Setup the account PIN. If the account settings allow PIN login, then
     * this will setup the account PIN package as well.
     */
    void pinLoginSetup() throws AirbitzException {
        Settings settings = settings();
        settings.settings().setBDisablePINLogin(false);
        settings.save();
    }

    /**
     * Setup the account PIN. If the account settings allow PIN login, then
     * this will setup the account PIN package as well.
     */
    void pinLoginDisable() throws AirbitzException {
        Settings settings = settings();
        settings.settings().setBDisablePINLogin(true);
        settings.save();
    }

    /**
     * Log the account out. This stops the exchange rate, data sync and bitcoin
     * network threads.
     */
    public void logout() {
        mEngine.stop();
        mLoggedIn = false;
        mApi.mAccounts.remove(this);
        mApi.destroy();
    }

    /**
     * Returns a list of the wallet ids for this account. This can be called
     * before the wallets have all be loaded.
     * @return list of wallet ids
     */
    public List<String> walletIds() {
        tABC_Error Error = new tABC_Error();
        List<String> uuids = new ArrayList<String>();

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int pUCount = core.int_to_uint(pCount);

        SWIGTYPE_p_long aUUIDS = core.new_longp();
        SWIGTYPE_p_p_p_char pppUUIDs = core.longp_to_pppChar(aUUIDS);

        tABC_CC result = core.ABC_GetWalletUUIDs(mUsername, mPassword,
                pppUUIDs, pUCount, Error);
        if (tABC_CC.ABC_CC_Ok == result)
        {
            if (core.longp_value(aUUIDS)!=0)
            {
                int count = core.intp_value(pCount);
                long base = core.longp_value(aUUIDS);
                for (int i = 0; i < count; i++)
                {
                    Jni.pLong temp = new Jni.pLong(base + i * 4);
                    long start = core.longp_value(temp);
                    if(start!=0) {
                        uuids.add(Jni.getStringAtPtr(start));
                    }
                }
            }
        }
        return uuids;
    }

    static Object WALLET_LOCK = new Object();
    /**
     * Returns a list of the wallets for this account, include Archived
     * wallets.  @return list of wallets
     */
    public synchronized List<Wallet> wallets() {
        if (mCachedWallets != null) {
            synchronized(WALLET_LOCK) {
                return new ArrayList<Wallet>(mCachedWallets);
            }
        } else {
            return null;
        }
    }

    synchronized void updateWallets(List<Wallet> wallets) {
        synchronized(WALLET_LOCK) {
            mCachedWallets = wallets;
        }
    }

    /**
     * Returns a list of the non-archived wallets for this account
     * @return list of non-archived wallets
     */
    public List<Wallet> activeWallets() {
        List<Wallet> wallets = wallets();
        if (wallets == null) {
            return null;
        }
        List<Wallet> out = new ArrayList<Wallet>();
        for (Wallet w: wallets) {
            if (!w.isArchived()) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * Create a new wallet for this account.
     * @param walletName the name of the wallet. This can be changed later.
     * @param currency the currency symbol such as "USD" or "EUR", for the fiat
     *      currency of this wallet.
     * @return true if wallet was successfully created
     */
    public boolean createWallet(String walletName, String currency) {
        AirbitzCore.logi("createWallet(" + walletName + "," + currency + ")");
        tABC_Error pError = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        int currencyNum = Currencies.instance().map(currency);
        tABC_CC result = core.ABC_CreateWallet(
                mUsername, mPassword,
                walletName, currencyNum, ppChar, pError);
        if (result == tABC_CC.ABC_CC_Ok) {
            mEngine.startWatchers();
            mEngine.requestExchangeRateUpdate(this, currency);
            reloadWallets();
            return true;
        } else {
            AirbitzCore.loge("Create wallet failed - "+pError.getSzDescription()+", at "+pError.getSzSourceFunc());
            return result == tABC_CC.ABC_CC_Ok;
        }
    }

    /**
     * This can be used to change or set the password for the account.
     * @param password
     */
    public void passwordChange(String password) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        tABC_CC cc = core.ABC_ChangePassword(
            mUsername, password, password, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
        mPassword = password;
        // Set the pin again
        // This sets up the pin package again.
        pin(pin());
    }

    /**
     * Used to set the recovery questions and answers for an account.
     * @param questions an of the selected recovery questions
     * @param answers an array of recovery answers
     */
    public void recoverySetup(String[] questions, String[] answers) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_SetAccountRecoveryQuestions(mUsername, mPassword,
                Utils.arrayToString(questions), Utils.arrayToString(answers), error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    public String setupRecoveryQuestions2(String [] questions, String[] answers) throws AirbitzException {
        tABC_Error error = new tABC_Error();
//        core.ABC_SetAccountRecoveryQuestions(mUsername, mPassword,
//                Utils.arrayToString(questions), Utils.arrayToString(answers), error);
//        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
//            throw new AirbitzException(error.getCode(), error);
//        }
        return "iamarecoverytokenreallyiam1234";
    }

    /**
     * Fetch a wallet by wallet id.
     * @param id the wallet id
     * @return a wallet object
     */
    public Wallet wallet(String id) {
        if (id == null) {
            return null;
        }
        List<Wallet> wallets = wallets();
        if (wallets == null) {
            return null;
        }
        for (Wallet w : wallets) {
            if (id.equals(w.id())) {
                return w;
            }
        }
        return null;
    }

    /**
     * Reorder the account's wallets. wallets() and activeWallets() will change
     * the order of what is returned.
     * @param wallets
     */
    public void walletReorder(List<Wallet> wallets) {
        boolean archived = false; // non-archive
        StringBuffer uuids = new StringBuffer("");
        for (Wallet wallet : wallets) {
            uuids.append(wallet.id()).append("\n");
        }

        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_SetWalletOrder(
            mUsername, mPassword,
            uuids.toString().trim(), error);
        if (result != tABC_CC.ABC_CC_Ok) {
            AirbitzCore.loge("Error: CoreBridge.setWalletOrder" + error.getSzDescription());
        }
        reloadWallets();
    }

    void sendReloadWallets() {
        mEngine.sendReloadWallets();
    }

    /**
     * Request the wallets be reloaded. This is an asynchronous call and will
     * return immediately.
     */
    void reloadWallets() {
        mEngine.reloadWallets();
    }

    /**
     * Starts the bitcoin, exchange rate and data sync backend engines. This
     * should be called after a user logs into their account.
     */
    public void startBackgroundTasks() {
        mEngine.start();
    }

    /**
     * Stop the bitcoin, exchange rate and data sync backend engines. This
     * should not be needed to be called. If your application is entering the
     * background and you wish to stop the background jobs call {@link AirbitzCore
     * #background background}.
     */
    public void stopBackgroundTasks() {
        mEngine.stop();
    }

    /**
     * BitidSignature is a small class which holds the address and signature to use for a
     * Bitid login. @see <a href="https://github.com/bitid/bitid">https://github.com/bitid/bitid</a>
     * for more details on Bitid.
     */
    public static class BitidSignature {
        public String address;
        public String signature;
    }

    /**
     * Parse a Bitid URI, and return the domain of the URI.
     * @param uri
     * @return the domain of the URI
     */
    public String parseBitidUri(String uri) {
        tABC_Error error = new tABC_Error();
        String urlDomain = null;

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppDomain = core.longp_to_ppChar(lp);

        SWIGTYPE_p_long lp2 = core.new_longp();
        SWIGTYPE_p_p_char ppBitIDCallbackURI = core.longp_to_ppChar(lp2);

        core.ABC_BitidParseUri(mUsername, null, uri, ppDomain, ppBitIDCallbackURI, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            urlDomain = Jni.getStringAtPtr(core.longp_value(lp));
        }
        return urlDomain;
    }

    /**
     * Sign the message using the Bitid key requested by the URI
     * @param uri
     * @param message
     * @return the tuple of address and the signature
     */
    public BitidSignature bitidSign(String uri, String message) {
        BitidSignature bitid = new BitidSignature();

        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long pAddress = core.new_longp();
        SWIGTYPE_p_p_char ppAddress = core.longp_to_ppChar(pAddress);
        SWIGTYPE_p_long pSignature = core.new_longp();
        SWIGTYPE_p_p_char ppSignature = core.longp_to_ppChar(pSignature);

        tABC_CC result = core.ABC_BitidSign(
            mUsername, mPassword, uri, message, ppAddress, ppSignature, error);
        if (result == tABC_CC.ABC_CC_Ok) {
            bitid.address = Jni.getStringAtPtr(core.longp_value(pAddress));
            bitid.signature = Jni.getStringAtPtr(core.longp_value(pSignature));
        }
        return bitid;
    }

    /**
     * Login to the URI using Bitid.
     * @param uri
     * @return true if the login was successful
     */
    public boolean bitidLogin(String uri) {
        tABC_Error error = new tABC_Error();
        core.ABC_BitidLogin(mUsername, null, uri, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    void restoreConnectivity() {
        mEngine.restoreConnectivity();
    }

    void lostConnectivity() {
        mEngine.lostConnectivity();
    }

    /**
     * Request an exchange rate update. This is a non-blocking call, which will
     * return immediately.
     */
    public void updateExchangeRates() {
        mEngine.updateExchangeRates();
    }

    /**
     * Used to check if OTP is enabled for this account.
     * @return true if OTP is enabled
     */
    public boolean isOtpEnabled() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long ptimeout = core.new_longp();
        SWIGTYPE_p_int lp = core.new_intp();
        SWIGTYPE_p_bool pbool = Jni.newBool(Jni.getCPtr(lp));

        core.ABC_OtpAuthGet(
            mUsername, mPassword,
            pbool, ptimeout, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
        return core.intp_value(lp) == 1;
    }

    /**
     * Enable OTP for this account.
     */
    public void otpEnable() throws AirbitzException {
        otpEnable(OTP_RESET_DELAY_SECS);
    }
    public void otpEnable(int timeout) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpAuthSet(mUsername, mPassword, timeout, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     * Disable OTP for this account on the server and removes the key from the
     * local device.
     */
    public void otpDisable() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpAuthRemove(mUsername, mPassword, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            core.ABC_OtpKeyRemove(mUsername, error);
        } else {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     * Cancel the OTP reset request.
     */
    public void otpResetCancel() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpResetRemove(mUsername, mPassword, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     *
     */
    public boolean isOtpResetPending() throws AirbitzException {
        return mApi.isOtpResetPending(username());
    }

    /**
     * Retrieve the OTP secret.
     * @return an OTP secret for the current account, null if one does not exist
     */
    public String otpSecret() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);
        tABC_CC cc = core.ABC_OtpKeyGet(mUsername, ppChar, error);
        String secret = cc == tABC_CC.ABC_CC_Ok ? Jni.getStringAtPtr(core.longp_value(lp)) : null;
        return secret;
    }

    /**
     * Set the OTP secret for the account.
     * @param secret the OTP secret for this account
     */
    public void otpSecret(String secret) throws AirbitzException {
        mApi.otpKeySet(username(), secret);
    }

    /**
     * Deletes the bitcoin network cache. This can be used to reset the account
     * if a user wishes to resync with the blockchain. {@link
     * #stopBackgroundTasks stopBackgroundTasks} should be called first. When
     * this is finished, {@link #startBackgroundTasks startBackgroundTasks} can
     * be called to begin sync-ing with the network.
     */
    public void clearBlockchainCache() {
        mEngine.deleteWatcherCache();
    }

    long mLastBackgroundTime = 0;
    boolean isExpired() {
        if (mLastBackgroundTime == 0) {
            return false;
        }
        long milliDelta = (System.currentTimeMillis() - mLastBackgroundTime);
        Settings settings = settings();
        if (settings != null) {
            if (milliDelta > settings.secondsAutoLogout() * 1000) {
                return true;
            }
        }
        return false;
    }
}
