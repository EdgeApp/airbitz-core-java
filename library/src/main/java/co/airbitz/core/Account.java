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
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

public class Account {
    private static String TAG = Account.class.getSimpleName();

    public static int OTP_RESET_DELAY_SECS = 60 * 60 * 24 * 7;

    private static int ABC_EXCHANGE_RATE_REFRESH_INTERVAL_SECONDS = 60;
    private static int ABC_SYNC_REFRESH_INTERVAL_SECONDS = 30;
    private static final int TX_LOADED_DELAY = 1000 * 20;

    public static int ABC_DENOMINATION_BTC = 0;
    public static int ABC_DENOMINATION_MBTC = 1;
    public static int ABC_DENOMINATION_UBTC = 2;

    public static double SATOSHI_PER_BTC = 1E8;
    public static double SATOSHI_PER_mBTC = 1E5;
    public static double SATOSHI_PER_uBTC = 1E2;

    private String mUsername;
    private String mPassword;
    private AirbitzCore mApi;
    private Categories mCategories;
    private AccountSettings mSettings;

    private boolean mTwoFactorOn = false;

    public native boolean registerAsyncCallback();

    public interface Callbacks {
        public void userRemotePasswordChange();
        public void userLoggedOut();
        public void userAccountChanged();
        public void userWalletsLoading();
        public void userWalletStatusChange(int loaded, int total);
        public void userWalletsLoaded();
        public void userWalletsChanged();
        public void userOTPRequired(String secret);
        public void userOtpResetPending();
        public void userExchangeRateChanged();
        public void userBlockHeightChanged();
        public void userBalanceUpdate();
        public void userIncomingBitcoin(Wallet wallet, Transaction transaction);
        public void userSweep(Wallet wallet, Transaction transaction);

        public void userBitcoinLoading();
        public void userBitcoinLoaded();
    }
    private Callbacks mCallbacks;

    Account(AirbitzCore api, String username, String password) {
        mApi = api;
        mUsername = username;
        mPassword = password;
        mCategories = new Categories(this);

        if (registerAsyncCallback()) {
            AirbitzCore.debugLevel(1, "Registered for core callbacks");
        }
        // Build initial settings file
        settings();
    }

    public void setCallbacks(Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    public String getUsername() {
        return mUsername;
    }

    public boolean wasPasswordLogin() {
        return mPassword != null;
    }

    protected String getPassword() {
        return mPassword;
    }

    public boolean isLoggedIn() {
        return mUsername != null;
    }

    public Categories categories() {
        return mCategories;
    }

    public DataStore data(String storeId) {
        return new DataStore(this, storeId);
    }

    public AccountSettings settings() {
        if (mSettings != null) {
            return mSettings;
        }
        try {
            mSettings = new AccountSettings(this).load();
            return mSettings;
        } catch (AirbitzException e) {
            AirbitzCore.debugLevel(1, "settings error:");
            return null;
        }
    }

    public String getUserCurrencyAcronym() {
        AccountSettings settings = settings();
        if (settings == null) {
            return Currencies.instance().currencyCodeLookup(840);
        } else {
            return Currencies.instance().currencyCodeLookup(settings.settings().getCurrencyNum());
        }
    }

    public String getUserCurrencySymbol() {
        AccountSettings settings = settings();
        if (settings == null) {
            return Currencies.instance().currencySymbolLookup(840);
        } else {
            return Currencies.instance().currencySymbolLookup(settings.settings().getCurrencyNum());
        }
    }

    public String GetUserPIN() {
        AccountSettings settings = settings();
        if (settings != null) {
            return settings.settings().getSzPIN();
        }
        return "";
    }

    public void SetPin(String pin) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        tABC_CC cc = core.ABC_SetPIN(mUsername, mPassword, pin, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(null, error.getCode(), error);
        }
    }

    public String getDefaultBTCDenomination() {
        AccountSettings settings = settings();
        if(settings == null) {
            return "";
        }
        BitcoinDenomination bitcoinDenomination =
            settings.getBitcoinDenomination();
        if (bitcoinDenomination == null) {
            AirbitzCore.debugLevel(1, "Bad bitcoin denomination from core settings");
            return "";
        }
        return mBTCDenominations[bitcoinDenomination.getDenominationType()];
    }

    public String getUserBTCSymbol() {
        AccountSettings settings = settings();
        if (settings == null) {
            return "";
        }
        BitcoinDenomination bitcoinDenomination =
            settings.getBitcoinDenomination();
        if (bitcoinDenomination == null) {
            AirbitzCore.debugLevel(1, "Bad bitcoin denomination from core settings");
            return "";
        }
        return mBTCSymbols[bitcoinDenomination.getDenominationType()];
    }

    public boolean incrementPinCount() {
        AccountSettings settings = settings();
        if (settings == null) {
            return false;
        }
        int pinLoginCount =
            settings.settings().getPinLoginCount();
        pinLoginCount++;
        settings.settings().setPinLoginCount(pinLoginCount);
        try {
            settings.save();
            if (pinLoginCount == 3
                    || pinLoginCount == 10
                    || pinLoginCount == 40
                    || pinLoginCount == 100) {
                return true;
            }
        } catch (AirbitzException e) {
            AirbitzCore.debugLevel(1, "incrementPinCount error:");
            return false;
        }
        return false;
    }


    public void logout() {
        stopAllAsyncUpdates();
        mSettings = null;
        mCachedWallets = null;
        mDataFetched = false;

        // Wait for data sync to exit gracefully
        AsyncTask[] as = new AsyncTask[] {
            mPinSetup, mReloadWalletTask
        };
        for (AsyncTask a : as) {
            if (a != null) {
                a.cancel(true);
                try {
                    a.get(1000, TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.CancellationException e) {
                    AirbitzCore.debugLevel(1, "task cancelled");
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
            }
        }
        mApi.destroy();
    }

    private Map<String, Thread> mWatcherTasks = new ConcurrentHashMap<String, Thread>();
    public void startWatchers() {
        List<String> wallets = getWalletIds();
        for (final String uuid : wallets) {
            startWatcher(uuid);
        }
        if (mDataFetched) {
            connectWatchers();
        }
    }

    private void startWatcher(final String uuid) {
        mWatcherHandler.post(new Runnable() {
            public void run() {
                if (uuid != null && !mWatcherTasks.containsKey(uuid)) {
                    tABC_Error error = new tABC_Error();
                    core.ABC_WatcherStart(mUsername, mPassword, uuid, error);
                    Utils.printABCError(error);
                    AirbitzCore.debugLevel(1, "Started watcher for " + uuid);

                    Thread thread = new Thread(new WatcherRunnable(uuid));
                    thread.start();

                    watchAddresses(uuid);

                    if (mDataFetched) {
                        connectWatcher(uuid);
                    }
                    mWatcherTasks.put(uuid, thread);

                    // Request a data sync as soon as watcher is started
                    requestWalletDataSync(uuid);
                    mMainHandler.sendEmptyMessage(RELOAD);
                }
            }
        });
    }

    public void connectWatchers() {
        List<String> wallets = getWalletIds();
        for (final String uuid : wallets) {
            connectWatcher(uuid);
        }
    }

    public void connectWatcher(final String uuid) {
        mWatcherHandler.post(new Runnable() {
            public void run() {
                tABC_Error error = new tABC_Error();
                core.ABC_WatcherConnect(uuid, error);
                Utils.printABCError(error);
                watchAddresses(uuid);
            }
        });
    }

    public void disconnectWatchers() {
        mWatcherHandler.post(new Runnable() {
            public void run() {
                for (String uuid : mWatcherTasks.keySet()) {
                    tABC_Error error = new tABC_Error();
                    core.ABC_WatcherDisconnect(uuid, error);
                }
            }
        });
    }

    private void watchAddresses(final String uuid) {
        tABC_Error error = new tABC_Error();
        core.ABC_WatchAddresses(mUsername, mPassword, uuid, error);
        Utils.printABCError(error);
    }

    public void waitOnWatchers() {
        mWatcherHandler.sendEmptyMessage(LAST);
        while (mWatcherHandler != null && mWatcherHandler.hasMessages(LAST)) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }
        }
    }

    /*
     * This thread will block as long as the watchers are running
     */
    private class WatcherRunnable implements Runnable {
        private final String uuid;

        WatcherRunnable(final String uuid) {
            this.uuid = uuid;
        }

        public void run() {
            tABC_Error error = new tABC_Error();

            int result = Jni.coreWatcherLoop(uuid, Jni.getCPtr(error));
        }
    }

    public void stopWatchers() {
        tABC_Error error = new tABC_Error();
        for (String uuid : mWatcherTasks.keySet()) {
            core.ABC_WatcherStop(uuid, error);
        }
        // Wait for all of the threads to finish.
        for (Thread thread : mWatcherTasks.values()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "", e);
            }
        }
        for (String uuid : mWatcherTasks.keySet()) {
            core.ABC_WatcherDelete(uuid, error);
        }
        mWatcherTasks.clear();
    }

    public void stopWatcher(String uuid) {
        tABC_Error error = new tABC_Error();
        core.ABC_WatcherStop(uuid, error);
        core.ABC_WatcherDelete(uuid, error);
        mWatcherTasks.remove(uuid);
    }

    public void deleteWatcherCache() {
        tABC_Error error = new tABC_Error();
        List<String> uuids = getWalletIds();
        for (String uuid : uuids) {
            core.ABC_WatcherDeleteCache(uuid, error);
        }
    }

    public List<String> getWalletIds() {
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

    private List<Wallet> mCachedWallets = null;
    public List<Wallet> getWallets() {
        return mCachedWallets;
    }

    public List<Wallet> getActiveWallets() {
        List<Wallet> wallets = getWallets();
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

    private Wallet getWalletFromCore(String uuid) {
        tABC_CC result;
        tABC_Error error = new tABC_Error();

        Wallet wallet = new Wallet(this, uuid);
        if (null != mWatcherTasks.get(uuid)) {
            // Load Wallet name
            SWIGTYPE_p_long pName = core.new_longp();
            SWIGTYPE_p_p_char ppName = core.longp_to_ppChar(pName);
            result = core.ABC_WalletName(mUsername, uuid, ppName, error);
            if (result == tABC_CC.ABC_CC_Ok) {
                wallet.name(Jni.getStringAtPtr(core.longp_value(pName)));
            }

            // Load currency
            SWIGTYPE_p_int pCurrency = core.new_intp();
            SWIGTYPE_p_unsigned_int upCurrency = core.int_to_uint(pCurrency);

            result = core.ABC_WalletCurrency(mUsername, uuid, pCurrency, error);
            if (result == tABC_CC.ABC_CC_Ok) {
                wallet.currencyNum(core.intp_value(pCurrency));
            } else {
                wallet.currencyNum(-1);
            }

            // Load balance
            SWIGTYPE_p_int64_t l = core.new_int64_tp();
            result = core.ABC_WalletBalance(mUsername, uuid, l, error);
            if (result == tABC_CC.ABC_CC_Ok) {
                wallet.balance(Jni.get64BitLongAtPtr(Jni.getCPtr(l)));
            } else {
                wallet.balance(0);
            }
        }
        return wallet;
    }

    public boolean createWallet(String walletName, int currencyNum) {
        AirbitzCore.debugLevel(1, "createWallet(" + walletName + "," + currencyNum + ")");
        tABC_Error pError = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        tABC_CC result = core.ABC_CreateWallet(
                mUsername, mPassword,
                walletName, currencyNum, ppChar, pError);
        if (result == tABC_CC.ABC_CC_Ok) {
            startWatchers();
            return true;
        } else {
            AirbitzCore.debugLevel(1, "Create wallet failed - "+pError.getSzDescription()+", at "+pError.getSzSourceFunc());
            return result == tABC_CC.ABC_CC_Ok;
        }
    }

    public void passwordSetup(String password) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        tABC_CC cc = core.ABC_ChangePassword(
            mUsername, password, password, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
        mPassword = password;
    }

    public void recoverySetup(String recoveryAnswers) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        tABC_CC cc = core.ABC_ChangePasswordWithRecoveryAnswers(
                        mUsername, recoveryAnswers, mPassword, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
    }

    public void saveRecoveryAnswers(String questions, String answers) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_SetAccountRecoveryQuestions(mUsername,
                mPassword, questions, answers, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
    }

    public Wallet getWallet(String uuid) {
        if (uuid == null) {
            return null;
        }
        List<Wallet> wallets = getWallets();
        if (wallets == null) {
            return null;
        }
        for (Wallet w : wallets) {
            if (uuid.equals(w.id())) {
                return w;
            }
        }
        return null;
    }

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
            AirbitzCore.debugLevel(1, "Error: CoreBridge.setWalletOrder" + error.getSzDescription());
        }
    }

    void sendReloadWallets() {
        mMainHandler.sendEmptyMessage(RELOAD);
    }

    ReloadWalletTask mReloadWalletTask = null;
    public void reloadWallets() {
        if (mReloadWalletTask == null && isLoggedIn()) {
            mReloadWalletTask = new ReloadWalletTask();
            mReloadWalletTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * Reload the wallet list on async thread and alert any listener
     */
    private class ReloadWalletTask extends AsyncTask<Void, Void, List<Wallet>> {

        @Override
        protected List<Wallet> doInBackground(Void... params) {
            List<Wallet> wallets = new ArrayList<Wallet>();
            List<String> uuids = getWalletIds();
            for (String uuid : uuids) {
                wallets.add(getWalletFromCore(uuid));
            }
            return wallets;
        }

        @Override
        protected void onPostExecute(List<Wallet> walletList) {
            mCachedWallets = walletList;
            if (mCallbacks != null) {
                mCallbacks.userWalletsChanged();
            }
            mReloadWalletTask = null;
        }
    }

    public void pinSetup() {
        if (mPinSetup == null && isLoggedIn()) {
            // Delay PinSetup after getting transactions
            mMainHandler.postDelayed(delayedPinSetup, 1000);
        }
    }

    public tABC_CC pinSetupBlocking() {
        AccountSettings settings = settings();
        if (settings != null) {
            String username = mUsername;
            String pin = settings.settings().getSzPIN();
            tABC_Error pError = new tABC_Error();
            return core.ABC_PinSetup(username, pin, pError);
        }
        return tABC_CC.ABC_CC_Error;
    }

    final Runnable delayedPinSetup = new Runnable() {
        public void run() {
            mPinSetup = new PinSetupTask();
            mPinSetup.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    };

    private PinSetupTask mPinSetup;
    private class PinSetupTask extends AsyncTask<Void, Void, tABC_CC> {
        @Override
        protected tABC_CC doInBackground(Void... params) {
            return pinSetupBlocking();
        }

        @Override
        protected void onPostExecute(tABC_CC cc) {
            mPinSetup = null;
        }

        @Override
        protected void onCancelled() {
            mPinSetup = null;
        }
    }

    public void pinLoginDelete(String username) {
        tABC_Error pError = new tABC_Error();
        tABC_CC result = core.ABC_PinLoginDelete(username, pError);
    }

    private String mIncomingWallet;
    private String mIncomingTxId;

    final Runnable mNotifyBitcoinLoaded = new Runnable() {
        public void run() {
            if (mCallbacks != null) {
                mCallbacks.userBitcoinLoaded();
            }
        }
    };

    final Runnable IncomingBitcoinUpdater = new Runnable() {
        public void run() {
            Wallet wallet = getWallet(mIncomingWallet);
            Transaction tx = wallet.getTransaction(mIncomingTxId);
            if (mCallbacks != null) {
                mCallbacks.userIncomingBitcoin(wallet, tx);
            }
        }
    };

    final Runnable BlockHeightUpdater = new Runnable() {
        public void run() {
            mSettings = null;
            if (mCallbacks != null) {
                mCallbacks.userBlockHeightChanged();
            }
        }
    };

    final Runnable DataSyncUpdater = new Runnable() {
        public void run() {
            mSettings = null;
            startWatchers();
            reloadWallets();
            if (mCallbacks != null) {
                mCallbacks.userAccountChanged();
            }
        }
    };

    private void receiveDataSyncUpdate() {
        mMainHandler.removeCallbacks(DataSyncUpdater);
        mMainHandler.postDelayed(DataSyncUpdater, 1000);
        mMainHandler.removeCallbacks(mNotifyBitcoinLoaded);
        mMainHandler.postDelayed(mNotifyBitcoinLoaded, TX_LOADED_DELAY);
    }

    public void startAllAsyncUpdates() {
        mMainHandler = new MainHandler();

        HandlerThread ht = new HandlerThread("Data Handler");
        ht.start();
        mDataHandler = new DataHandler(ht.getLooper());

        ht = new HandlerThread("Exchange Handler");
        ht.start();
        mExchangeHandler = new ExchangeHandler(ht.getLooper());

        ht = new HandlerThread("ABC Core");
        ht.start();
        mCoreHandler = new Handler(ht.getLooper());

        ht = new HandlerThread("Watchers");
        ht.start();
        mWatcherHandler = new Handler(ht.getLooper());

        final List<String> uuids = getWalletIds();
        final int walletCount = uuids.size();
        mCoreHandler.post(new Runnable() {
            public void run() {
                if (mCallbacks != null) {
                    mCallbacks.userWalletsLoading();
                }
            }
        });
        for (final String uuid : uuids) {
            mCoreHandler.post(new Runnable() {
                public void run() {
                    tABC_Error error = new tABC_Error();
                    core.ABC_WalletLoad(mUsername, uuid, error);

                    startWatcher(uuid);
                    mMainHandler.sendEmptyMessage(RELOAD);
                    if (mCallbacks != null) {
                        mCallbacks.userWalletStatusChange(mWatcherTasks.size(), walletCount);
                    }
                }
            });
        }
        mCoreHandler.post(new Runnable() {
            public void run() {
                if (mCallbacks != null) {
                    mCallbacks.userWalletsLoaded();
                }
                startBitcoinUpdates();
                startExchangeRateUpdates();
                startFileSyncUpdates();

                mMainHandler.sendEmptyMessage(RELOAD);
            }
        });
    }

    public void stopAllAsyncUpdates() {
        if (mCoreHandler == null
                || mDataHandler == null
                || mExchangeHandler == null
                || mWatcherHandler == null
                || mMainHandler == null) {
            return;
        }
        mCoreHandler.removeCallbacksAndMessages(null);
        mCoreHandler.sendEmptyMessage(LAST);
        mDataHandler.removeCallbacksAndMessages(null);
        mDataHandler.sendEmptyMessage(LAST);
        mExchangeHandler.removeCallbacksAndMessages(null);
        mExchangeHandler.sendEmptyMessage(LAST);
        mWatcherHandler.removeCallbacksAndMessages(null);
        mWatcherHandler.sendEmptyMessage(LAST);
        mMainHandler.removeCallbacksAndMessages(null);
        mMainHandler.sendEmptyMessage(LAST);
        while (mCoreHandler.hasMessages(LAST)
                || mWatcherHandler.hasMessages(LAST)
                || mExchangeHandler.hasMessages(LAST)
                || mMainHandler.hasMessages(LAST)) {
            try {
                AirbitzCore.debugLevel(1,
                    "Data: " + mDataHandler.hasMessages(LAST) + ", " +
                    "Core: " + mCoreHandler.hasMessages(LAST) + ", " +
                    "Watcher: " + mWatcherHandler.hasMessages(LAST) + ", " +
                    "Exchange: " + mExchangeHandler.hasMessages(LAST) + ", " +
                    "Main: " + mMainHandler.hasMessages(LAST));
                Thread.sleep(200);
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }
        }

        stopWatchers();
        stopExchangeRateUpdates();
        stopFileSyncUpdates();
    }

    public static class BitidSignature {
        public String address;
        public String signature;
    }

    public String parseBitidUri(String uri) {
        tABC_Error error = new tABC_Error();
        String urlDomain = null;

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        core.ABC_BitidParseUri(mUsername, null, uri, ppChar, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            urlDomain = Jni.getStringAtPtr(core.longp_value(lp));
        }
        return urlDomain;
    }

    public BitidSignature bitidSignature(String uri, String message) {
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

    public boolean bitidLogin(String uri) {
        tABC_Error error = new tABC_Error();
        core.ABC_BitidLogin(mUsername, null, uri, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    public boolean hasRecoveryQuestionsSet() {
        try {
            String qstring = mApi.getRecoveryQuestionsForUser(mUsername);
            if (qstring != null) {
                String[] qs = qstring.split("\n");
                if (qs.length > 1) {
                    // Recovery questions set
                    return true;
                }
            }
        } catch (AirbitzException e) {
            AirbitzCore.debugLevel(1, "hasRecoveryQuestionsSet error:");
        }
        return false;
    }

    private Handler mMainHandler;
    private Handler mCoreHandler;
    private Handler mWatcherHandler;
    private Handler mDataHandler;
    private Handler mExchangeHandler;
    private boolean mDataFetched = false;

    final static int RELOAD = 0;
    final static int REPEAT = 1;
    final static int LAST = 2;

    private class DataHandler extends Handler {
        DataHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(final Message msg) {
            if (REPEAT == msg.what) {
                postDelayed(new Runnable() {
                    public void run() {
                        syncAllData();
                    }
                }, ABC_SYNC_REFRESH_INTERVAL_SECONDS * 1000);
            }
        }
    }

    private class ExchangeHandler extends Handler {
        ExchangeHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(final Message msg) {
            if (REPEAT == msg.what) {
                postDelayed(new Runnable() {
                    public void run() {
                        updateExchangeRates();
                    }
                }, 1000 * ABC_EXCHANGE_RATE_REFRESH_INTERVAL_SECONDS);
            }
        }
    }

    private class MainHandler extends Handler {
        MainHandler() {
            super();
        }

        @Override
        public void handleMessage(final Message msg) {
            if (RELOAD == msg.what) {
                reloadWallets();
            }
        }
    }

    void restoreConnectivity() {
        connectWatchers();
        mCoreHandler.post(new Runnable() {
            public void run() {
                startExchangeRateUpdates();
            }
        });
        mCoreHandler.post(new Runnable() {
            public void run() {
                startFileSyncUpdates();
            }
        });
    }

    void lostConnectivity() {
        stopExchangeRateUpdates();
        stopFileSyncUpdates();
        disconnectWatchers();
    }

    public void stopExchangeRateUpdates() {
        if (null != mExchangeHandler) {
            mExchangeHandler.removeCallbacksAndMessages(null);
            mExchangeHandler.sendEmptyMessage(LAST);
        }
    }

    public void startBitcoinUpdates() {
        if (mCallbacks != null) {
            mCallbacks.userBitcoinLoading();
        }
        mMainHandler.removeCallbacks(mNotifyBitcoinLoaded);
        mMainHandler.postDelayed(mNotifyBitcoinLoaded, TX_LOADED_DELAY);
    }

    public void startExchangeRateUpdates() {
        updateExchangeRates();
    }

    public void updateExchangeRates() {
        if (null == mExchangeHandler
            || mExchangeHandler.hasMessages(REPEAT)
            || mExchangeHandler.hasMessages(LAST)) {
            return;
        }

        List<Wallet> wallets = getWallets();
        if (isLoggedIn()
                && null != settings()
                && null != wallets) {
            requestExchangeRateUpdate(settings().getCurrencyNum());
            for (Wallet wallet : wallets) {
                if (wallet.currencyNum() != -1) {
                    requestExchangeRateUpdate(wallet.currencyNum());
                }
            }
            mMainHandler.post(new Runnable() {
                public void run() {
                    if (mCallbacks != null) {
                        mCallbacks.userExchangeRateChanged();
                    }
                }
            });
        }
        mExchangeHandler.sendEmptyMessage(REPEAT);
    }

    private void requestExchangeRateUpdate(final Integer currencyNum) {
        mExchangeHandler.post(new Runnable() {
            public void run() {
                tABC_Error error = new tABC_Error();
                core.ABC_RequestExchangeRateUpdate(mUsername,
                    mPassword, currencyNum, error);
            }
        });
    }

    public void stopFileSyncUpdates() {
        if (null != mDataHandler) {
            mDataHandler.removeCallbacksAndMessages(null);
        }
    }

    public void startFileSyncUpdates() {
        syncAllData();
    }

    public void syncAllData() {
        if (mDataHandler.hasMessages(REPEAT)
            || mDataHandler.hasMessages(LAST)) {
            return;
        }
        mDataHandler.post(new Runnable() {
            public void run() {
                mApi.generalInfoUpdate();
            }
        });
        mDataHandler.post(new Runnable() {
            public void run() {
                tABC_Error error = new tABC_Error();
                SWIGTYPE_p_long pdirty = core.new_longp();
                SWIGTYPE_p_bool dirty = Jni.newBool(Jni.getCPtr(pdirty));
                SWIGTYPE_p_long pchange = core.new_longp();
                SWIGTYPE_p_bool passwordChange = Jni.newBool(Jni.getCPtr(pchange));

                core.ABC_DataSyncAccount(mUsername, mPassword, dirty, passwordChange, error);
                if (error.getCode() == tABC_CC.ABC_CC_InvalidOTP) {
                    mMainHandler.post(new Runnable() {
                        public void run() {
                            if (isLoggedIn() && mCallbacks != null) {
                                mCallbacks.userOTPRequired(getTwoFactorSecret());
                            }
                        }
                    });
                } else if (Jni.getBytesAtPtr(Jni.getCPtr(pdirty), 1)[0] != 0) {
                    // Data changed remotel
                    receiveDataSyncUpdate();
                } else if (Jni.getBytesAtPtr(Jni.getCPtr(pchange), 1)[0] != 0) {
                    if (mCallbacks != null) {
                        mCallbacks.userRemotePasswordChange();
                    }
                }
            }
        });

        List<String> uuids = getWalletIds();
        for (String uuid : uuids) {
            requestWalletDataSync(uuid);
        }

        mDataHandler.post(new Runnable() {
            public void run() {
                boolean pending = false;
                try {
                    pending = mApi.isTwoFactorResetPending(mUsername);
                } catch (AirbitzException e) {
                    AirbitzCore.debugLevel(1, "mDataHandler.post error:");
                }
                final boolean isPending = pending;
                mMainHandler.post(new Runnable() {
                    public void run() {
                        if (!mDataFetched) {
                            mDataFetched = true;
                            connectWatchers();
                        }
                        if (isPending && mCallbacks != null) {
                            mCallbacks.userOtpResetPending();
                        }
                    }
                });
            }
        });
        // Repeat the data sync
        mDataHandler.sendEmptyMessage(REPEAT);
    }

    private void requestWalletDataSync(final String uuid) {
        mDataHandler.post(new Runnable() {
            public void run() {
                tABC_Error error = new tABC_Error();
                SWIGTYPE_p_long pdirty = core.new_longp();
                SWIGTYPE_p_bool dirty = Jni.newBool(Jni.getCPtr(pdirty));

                core.ABC_DataSyncWallet(mUsername, mPassword, uuid, dirty, error);
                mMainHandler.post(new Runnable() {
                    public void run() {
                        if (!mDataFetched) {
                            connectWatcher(uuid);
                        }
                    }
                });
                if (Jni.getBytesAtPtr(Jni.getCPtr(pdirty), 1)[0] != 0) {
                    receiveDataSyncUpdate();
                }
            }
        });
    }

    private void callbackAsyncBitcoinInfo(long asyncBitCoinInfo_ptr) {
        tABC_AsyncBitCoinInfo info = Jni.newAsyncBitcoinInfo(asyncBitCoinInfo_ptr);
        tABC_AsyncEventType type = info.getEventType();

        AirbitzCore.debugLevel(1, "asyncBitCoinInfo callback type = "+type.toString());
        if (type==tABC_AsyncEventType.ABC_AsyncEventType_IncomingBitCoin) {
            mIncomingWallet = info.getSzWalletUUID();
            mIncomingTxId = info.getSzTxID();

            // Notify app of new tx
            mMainHandler.removeCallbacks(IncomingBitcoinUpdater);
            mMainHandler.postDelayed(IncomingBitcoinUpdater, 300);

            // Notify progress bar more txs might be coming
            mMainHandler.removeCallbacks(mNotifyBitcoinLoaded);
            mMainHandler.postDelayed(mNotifyBitcoinLoaded, TX_LOADED_DELAY);
        } else if (type==tABC_AsyncEventType.ABC_AsyncEventType_BlockHeightChange) {
            mMainHandler.post(BlockHeightUpdater);
        /*
        } else if (type == tABC_AsyncEventType.ABC_AsyncEventType_IncomingSweep) {
            String txid = info.getSzTxID();
            long amount = get64BitLongAtPtr(Jni.getCPtr(info.getSweepSatoshi()));
            if (mCallbacks != null) {
                mCallbacks.userSweep(wallet, tx);
            }
        */
        }
    }

    private String[] mBTCDenominations = {"BTC", "mBTC", "bits"};
    private String[] mBTCSymbols = {"Ƀ ", "mɃ ", "ƀ "};

    public String formatDefaultCurrency(double in) {
        AccountSettings settings = settings();
        if (settings != null) {
            String pre = mBTCSymbols[settings.getBitcoinDenomination().getDenominationType()];
            String out = String.format("%.3f", in);
            return pre+out;
        }
        return "";
    }

    public String formatCurrency(double in, int currencyNum, boolean withSymbol) {
        return formatCurrency(in, currencyNum, withSymbol, 2);
    }

    public String formatCurrency(double in, int currencyNum, boolean withSymbol, int decimalPlaces) {
        String pre;
        String denom = Currencies.instance().currencySymbolLookup(currencyNum) + " ";
        if (in < 0)
        {
            in = Math.abs(in);
            pre = withSymbol ? "-" + denom : "-";
        } else {
            pre = withSymbol ? denom : "";
        }
        BigDecimal bd = new BigDecimal(in);
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

    public int userDecimalPlaces() {
        int decimalPlaces = 8; // for ABC_DENOMINATION_BTC
        AccountSettings settings = settings();
        if (settings == null) {
            return 2;
        }
        BitcoinDenomination bitcoinDenomination =
            settings.getBitcoinDenomination();
        if (bitcoinDenomination != null) {
            int label = bitcoinDenomination.getDenominationType();
            if (label == ABC_DENOMINATION_UBTC)
                decimalPlaces = 2;
            else if (label == ABC_DENOMINATION_MBTC)
                decimalPlaces = 5;
        }
        return decimalPlaces;
    }

    public String formatSatoshi(long amount) {
        return formatSatoshi(amount, true);
    }

    public String formatSatoshi(long amount, boolean withSymbol) {
        return formatSatoshi(amount, withSymbol, userDecimalPlaces());
    }

    public String formatSatoshi(long amount, boolean withSymbol, int decimals) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        int decimalPlaces = userDecimalPlaces();

        boolean negative = amount < 0;
        if(negative)
            amount = -amount;
        int result = Jni.FormatAmount(amount, Jni.getCPtr(ppChar), decimalPlaces, false, Jni.getCPtr(error));
        if ( result != 0)
        {
            return "";
        }
        else {
            decimalPlaces = decimals > -1 ? decimals : decimalPlaces;
            String pretext = "";
            if (negative) {
                pretext += "-";
            }
            if(withSymbol) {
                pretext += getUserBTCSymbol();
            }

            BigDecimal bd = new BigDecimal(amount);
            bd = bd.movePointLeft(decimalPlaces);

            DecimalFormat df = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.getDefault()));

            if(decimalPlaces == 5) {
                df = new DecimalFormat("#,##0.#####", new DecimalFormatSymbols(Locale.getDefault()));
            }
            else if(decimalPlaces == 8) {
                df = new DecimalFormat("#,##0.########", new DecimalFormatSymbols(Locale.getDefault()));
            }

            return pretext + df.format(bd.doubleValue());
        }
    }

    private int mCurrencyIndex = 0;
    public int SettingsCurrencyIndex() {
        int index = -1;
        int currencyNum;
        AccountSettings settings = settings();
        if(settings == null && mCurrencyIndex != 0) {
            currencyNum = mCurrencyIndex;
        }
        else {
            currencyNum = settings.settings().getCurrencyNum();
            mCurrencyIndex = currencyNum;
        }
        int[] currencyNumbers = Currencies.instance().getCurrencyNumberArray();

        for(int i=0; i<currencyNumbers.length; i++) {
            if(currencyNumbers[i] == currencyNum)
                index = i;
        }
        if((index==-1) || (index >= currencyNumbers.length)) { // default usd
            AirbitzCore.debugLevel(1, "currency index out of bounds "+index);
            index = currencyNumbers.length-1;
        }
        return index;
    }

    public int CurrencyIndex(int currencyNum) {
        int index = -1;
        int[] currencyNumbers = Currencies.instance().getCurrencyNumberArray();

        for(int i=0; i<currencyNumbers.length; i++) {
            if(currencyNumbers[i] == currencyNum)
                index = i;
        }
        if((index==-1) || (index >= currencyNumbers.length)) { // default usd
            AirbitzCore.debugLevel(1, "currency index out of bounds "+index);
            index = currencyNumbers.length-1;
        }
        return index;
    }

    public long denominationToSatoshi(String amount) {
        int decimalPlaces = userDecimalPlaces();

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

    public String BTCtoFiatConversion(int currencyNum) {
        AccountSettings settings = settings();
        if(settings != null) {
            BitcoinDenomination denomination =
                settings.getBitcoinDenomination();
            long satoshi = 100;
            int denomIndex = 0;
            int fiatDecimals = 2;
            String amtBTCDenom = "1 ";
            if (denomination != null) {
                if (denomination.getDenominationType()==ABC_DENOMINATION_BTC) {
                    satoshi = (long) SATOSHI_PER_BTC;
                    denomIndex = 0;
                    fiatDecimals = 2;
                    amtBTCDenom = "1 ";
                } else if (denomination.getDenominationType()==ABC_DENOMINATION_MBTC) {
                    satoshi = (long) SATOSHI_PER_mBTC;
                    denomIndex = 1;
                    fiatDecimals = 3;
                    amtBTCDenom = "1 ";
                } else if (denomination.getDenominationType()==ABC_DENOMINATION_UBTC) {
                    satoshi = (long) SATOSHI_PER_uBTC;
                    denomIndex = 2;
                    fiatDecimals = 3;
                    amtBTCDenom = "1000 ";
                }
            }
            double o = SatoshiToCurrency(satoshi, currencyNum);
            if (denomIndex == 2) {
                // unit of 'bits' is so small it's useless to show it's conversion rate
                // Instead show "1000 bits = $0.253 USD"
                o = o * 1000;
            }
            String currency = formatCurrency(o, currencyNum, true, fiatDecimals);
            String currencyLabel = Currencies.instance().currencyCodeLookup(currencyNum);
            return amtBTCDenom + mBTCDenominations[denomIndex] + " = " + currency + " " + currencyLabel;
        }
        return "";
    }

    public String FormatDefaultCurrency(long satoshi, boolean btc, boolean withSymbol) {
        AccountSettings settings = settings();
        if (settings != null) {
            int currencyNumber = settings.settings().getCurrencyNum();
            return FormatCurrency(satoshi, currencyNumber, btc, withSymbol);
        }
        return "";
    }

    public String FormatCurrency(long satoshi, int currencyNum, boolean btc, boolean withSymbol) {
        String out;
        if (!btc) {
            double o = SatoshiToCurrency(satoshi, currencyNum);
            out = formatCurrency(o, currencyNum, withSymbol);
        } else {
            out = formatSatoshi(satoshi, withSymbol, 2);
        }
        return out;
    }

    public double SatoshiToCurrency(long satoshi, int currencyNum) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_double currency = core.new_doublep();

        long out = Jni.satoshiToCurrency(mUsername, mPassword,
                satoshi, Jni.getCPtr(currency), currencyNum, Jni.getCPtr(error));

        return core.doublep_value(currency);
    }

    public long parseFiatToSatoshi(String amount, int currencyNum) {
        try {
             Number cleanAmount =
                new DecimalFormat().parse(amount, new ParsePosition(0));
             if (null == cleanAmount) {
                 return 0;
             }
            double currency = cleanAmount.doubleValue();
            long satoshi = CurrencyToSatoshi(currency, currencyNum);

            // Round up to nearest 1 bits, .001 mBTC, .00001 BTC
            satoshi = 100 * (satoshi / 100);
            return satoshi;

        } catch (NumberFormatException e) {
            /* Sshhhhh */
        }
        return 0;
    }

    public long CurrencyToSatoshi(double currency, int currencyNum) {
        tABC_Error error = new tABC_Error();
        tABC_CC result;
        SWIGTYPE_p_int64_t satoshi = core.new_int64_tp();
        SWIGTYPE_p_long l = core.p64_t_to_long_ptr(satoshi);

        result = core.ABC_CurrencyToSatoshi(mUsername, mPassword,
            currency, currencyNum, satoshi, error);

        return Jni.get64BitLongAtPtr(Jni.getCPtr(l));
    }

    public void otpAuthGet() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long ptimeout = core.new_longp();
        SWIGTYPE_p_int lp = core.new_intp();
        SWIGTYPE_p_bool pbool = Jni.newBool(Jni.getCPtr(lp));

        core.ABC_OtpAuthGet(
            mUsername, mPassword,
            pbool, ptimeout, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }

        mTwoFactorOn = core.intp_value(lp)==1;
    }

    public void otpSetup() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpAuthSet(mUsername, mPassword, OTP_RESET_DELAY_SECS, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            mTwoFactorOn = true;
        } else {
            throw new AirbitzException(null, error.getCode(), error);
        }
    }

    public void otpDisable() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpAuthRemove(mUsername, mPassword, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            mTwoFactorOn = false;
            core.ABC_OtpKeyRemove(mUsername, error);
        } else {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
    }

    public void otpResetCancel() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpResetRemove(mUsername, mPassword, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
    }

    public boolean passwordOk(String password) {
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
                AirbitzCore.debugLevel(1, "Password OK error:"+ error.getSzDescription());
            }
        }
        return check;
    }

    public boolean passwordExists() {
        tABC_Error pError = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool exists = Jni.newBool(Jni.getCPtr(lp));

        tABC_CC result = core.ABC_PasswordExists(mUsername, exists, pError);
        if(pError.getCode().equals(tABC_CC.ABC_CC_Ok)) {
            return Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        } else {
            AirbitzCore.debugLevel(1, "Password Exists error:"+pError.getSzDescription());
            return true;
        }
    }

    public void SetupDefaultCurrency() {
        AccountSettings settings = settings();
        if (settings == null) {
            return;
        }
        settings.setupDefaultCurrency();
    }

    public boolean isTwoFactorOn() {
        return mTwoFactorOn;
    }

    public String getTwoFactorSecret() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);
        tABC_CC cc = core.ABC_OtpKeyGet(mUsername, ppChar, error);
        String secret = cc == tABC_CC.ABC_CC_Ok ? Jni.getStringAtPtr(core.longp_value(lp)) : null;
        return secret;
    }

    boolean mOTPError = false;
    public boolean hasOTPError() {
        return mOTPError;
    }
    public void otpSetError(tABC_CC cc) {
        mOTPError = tABC_CC.ABC_CC_InvalidOTP == cc;
    }

    public void otpSetError(AirbitzException error) {
        mOTPError = error.isOtpError();
    }

    public void otpClearError() {
        mOTPError = false;
    }

    public byte[] getTwoFactorQRCode() {
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_unsigned_char ppData = core.longp_to_unsigned_ppChar(lp);

        SWIGTYPE_p_int pWidth = core.new_intp();
        SWIGTYPE_p_unsigned_int pWCount = core.int_to_uint(pWidth);

        tABC_Error error = new tABC_Error();
        tABC_CC cc = core.ABC_QrEncode(getTwoFactorSecret(), ppData, pWCount, error);
        if (cc == tABC_CC.ABC_CC_Ok) {
            int width = core.intp_value(pWidth);
            return Jni.getBytesAtPtr(core.longp_value(lp), width*width);
        } else {
            return null;
        }
    }

    public Bitmap getTwoFactorQRCodeBitmap() {
        byte[] array = getTwoFactorQRCode();
        if (null != array) {
            return mApi.qrEncode(array, (int) Math.sqrt(array.length), 4);
        } else {
            return null;
        }
    }

}
