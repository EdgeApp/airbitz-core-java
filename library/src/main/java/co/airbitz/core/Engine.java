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

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

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

public class Engine {
    private static String TAG = Account.class.getSimpleName();

    private static int ABC_EXCHANGE_RATE_REFRESH_INTERVAL_SECONDS = 60;
    private static int ABC_SYNC_REFRESH_INTERVAL_SECONDS = 30;
    private static final int TX_LOADED_DELAY = 1000 * 20;

    public native boolean registerAsyncCallback();

    private AirbitzCore mApi;
    private Account mAccount;

    private Handler mMainHandler;
    private Handler mCoreHandler;
    private Handler mWatcherHandler;
    private Handler mDataHandler;
    private Handler mExchangeHandler;
    private boolean mDataFetched = false;

    final static int RELOAD = 0;
    final static int REPEAT = 1;
    final static int LAST = 2;

    Engine(AirbitzCore api, Account account) {
        mApi = api;
        mAccount = account;

        if (registerAsyncCallback()) {
            AirbitzCore.logi("Registered for core callbacks");
        }
    }

    private Map<String, Thread> mWatcherTasks = new ConcurrentHashMap<String, Thread>();

    public void startWatchers() {
        List<String> wallets = mAccount.walletIds();
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
                    core.ABC_WatcherStart(mAccount.username(), mAccount.password(), uuid, error);
                    Utils.printABCError(error);
                    AirbitzCore.logi("Started watcher for " + uuid);

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
        List<String> wallets = mAccount.walletIds();
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
        core.ABC_WatchAddresses(mAccount.username(), mAccount.password(), uuid, error);
        Utils.printABCError(error);
    }

    public void waitOnWatchers() {
        mWatcherHandler.sendEmptyMessage(LAST);
        while (mWatcherHandler != null && mWatcherHandler.hasMessages(LAST)) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                AirbitzCore.loge(e.getMessage());
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
                AirbitzCore.loge(e.getMessage());
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
        List<String> uuids = mAccount.walletIds();
        for (String uuid : uuids) {
            core.ABC_WatcherDeleteCache(uuid, error);
        }
    }

    void sendReloadWallets() {
        mMainHandler.sendEmptyMessage(RELOAD);
    }

    ReloadWalletTask mReloadWalletTask = null;
    public void reloadWallets() {
        if (mReloadWalletTask == null && mAccount.isLoggedIn()) {
            mReloadWalletTask = new ReloadWalletTask();
            mReloadWalletTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private class ReloadWalletTask extends AsyncTask<Void, Void, List<Wallet>> {
        @Override
        protected List<Wallet> doInBackground(Void... params) {
            List<Wallet> wallets = new ArrayList<Wallet>();
            List<String> uuids = mAccount.walletIds();
            for (String uuid : uuids) {
                wallets.add(getWalletFromCore(uuid));
            }
            return wallets;
        }

        @Override
        protected void onPostExecute(List<Wallet> walletList) {
            mAccount.mCachedWallets = walletList;
            if (mAccount.mCallbacks != null) {
                mAccount.mCallbacks.userWalletsChanged();
            }
            mReloadWalletTask = null;
        }
    }

    final Runnable mNotifyBitcoinLoaded = new Runnable() {
        public void run() {
            if (mAccount.mCallbacks != null) {
                mAccount.mCallbacks.bitcoinLoaded();
            }
        }
    };

    String mIncomingWallet;
    String mIncomingTxId;

    final Runnable mIncomingBitcoinUpdater = new Runnable() {
        public void run() {
            if (mAccount.mCallbacks != null) {
                Wallet wallet = mAccount.wallet(mIncomingWallet);
                Transaction tx = wallet.transaction(mIncomingTxId);
                mAccount.mCallbacks.incomingBitcoin(wallet, tx);
            }
            mIncomingWallet = null;
            mIncomingTxId = null;
        }
    };

    final Runnable mBalanceUpdated = new Runnable() {
        public void run() {
            if (mAccount.mCallbacks != null) {
                mAccount.mCallbacks.balanceUpdate();
            }
        }
    };

    final Runnable mBlockHeightUpdater = new Runnable() {
        public void run() {
            mAccount.mSettings = null;
            if (mAccount.mCallbacks != null) {
                mAccount.mCallbacks.blockHeightChanged();
            }
        }
    };

    final Runnable mDataSyncUpdater = new Runnable() {
        public void run() {
            mAccount.mSettings = null;
            startWatchers();
            reloadWallets();
            if (mAccount.mCallbacks != null) {
                mAccount.mCallbacks.userAccountChanged();
            }
        }
    };

    private void receiveDataSyncUpdate() {
        mMainHandler.removeCallbacks(mDataSyncUpdater);
        mMainHandler.postDelayed(mDataSyncUpdater, BALANCE_CHANGE_DELAY);
    }

    public void start() {
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

        final List<String> uuids = mAccount.walletIds();
        final int walletCount = uuids.size();
        if (mAccount.mCallbacks != null) {
            mMainHandler.post(new Runnable() {
                public void run() {
                    mAccount.mCallbacks.userWalletsLoading();
                }
            });
        }
        for (final String uuid : uuids) {
            mCoreHandler.post(new Runnable() {
                public void run() {
                    tABC_Error error = new tABC_Error();
                    core.ABC_WalletLoad(mAccount.username(), uuid, error);

                    startWatcher(uuid);
                    if (mAccount.mCallbacks != null) {
                        mMainHandler.post(new Runnable() {
                            public void run() {
                                mAccount.mCallbacks.userWalletStatusChange(mWatcherTasks.size(), walletCount);
                            }
                        });
                    }
                    mMainHandler.sendEmptyMessage(RELOAD);
                }
            });
        }
        mCoreHandler.post(new Runnable() {
            public void run() {
                if (mAccount.mCallbacks != null) {
                    mMainHandler.post(new Runnable() {
                        public void run() {
                            mAccount.mCallbacks.userWalletsLoaded();
                        }
                    });
                }
                startBitcoinUpdates();
                startExchangeRateUpdates();
                startFileSyncUpdates();

                mMainHandler.sendEmptyMessage(RELOAD);
            }
        });
    }

    private void waitOnAsync() {
        AsyncTask[] as = new AsyncTask[] {
            mReloadWalletTask
        };
        for (AsyncTask a : as) {
            if (a != null) {
                a.cancel(true);
                try {
                    a.get(1000, TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.CancellationException e) {
                    AirbitzCore.loge("task cancelled");
                } catch (Exception e) {
                    AirbitzCore.loge(e.getMessage());
                }
            }
        }
    }

    public void stop() {
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
                AirbitzCore.logi(
                    "Data: " + mDataHandler.hasMessages(LAST) + ", " +
                    "Core: " + mCoreHandler.hasMessages(LAST) + ", " +
                    "Watcher: " + mWatcherHandler.hasMessages(LAST) + ", " +
                    "Exchange: " + mExchangeHandler.hasMessages(LAST) + ", " +
                    "Main: " + mMainHandler.hasMessages(LAST));
                Thread.sleep(200);
            } catch (Exception e) {
                AirbitzCore.loge(e.getMessage());
            }
        }

        stopWatchers();
        stopExchangeRateUpdates();
        stopFileSyncUpdates();
        waitOnAsync();
    }

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
        if (mAccount.mCallbacks != null) {
            mMainHandler.post(new Runnable() {
                public void run() {
                    mAccount.mCallbacks.bitcoinLoading();
                }
            });
        }
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

        List<Wallet> wallets = mAccount.wallets();
        if (mAccount.isLoggedIn()
                && null != mAccount.settings()
                && null != wallets) {

            requestExchangeRateUpdate(mAccount, mAccount.settings().currencyCode());
            for (Wallet wallet : wallets) {
                if (wallet.isSynced()) {
                    requestExchangeRateUpdate(mAccount, wallet.currencyCode());
                }
            }
            if (mAccount.mCallbacks != null) {
                mMainHandler.post(new Runnable() {
                    public void run() {
                        mAccount.mCallbacks.exchangeRateChanged();
                    }
                });
            }
        }
        mExchangeHandler.sendEmptyMessage(REPEAT);
    }

    void requestExchangeRateUpdate(final Account account, final String currency) {
        mExchangeHandler.post(new Runnable() {
            public void run() {
                AirbitzCore.getApi().exchangeCache().update(account, currency);
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

                core.ABC_DataSyncAccount(mAccount.username(), mAccount.password(), dirty, passwordChange, error);
                if (error.getCode() == tABC_CC.ABC_CC_InvalidOTP) {
                    final AirbitzException e = new AirbitzException(null, error.getCode(), error);
                    if (mAccount.isLoggedIn() && mAccount.mCallbacks != null) {
                        mMainHandler.post(new Runnable() {
                            public void run() {
                                // if the account has an OTP token, then its probably a skew problem
                                if (mAccount.otpSecret() != null) {
                                    mAccount.mCallbacks.otpSkew();
                                } else {
                                    mAccount.mCallbacks.otpRequired(e.otpResetDate());
                                }
                            }
                        });
                    }
                } else if (Jni.getBytesAtPtr(Jni.getCPtr(pdirty), 1)[0] != 0) {
                    // Data changed remotel
                    receiveDataSyncUpdate();
                } else if (Jni.getBytesAtPtr(Jni.getCPtr(pchange), 1)[0] != 0) {
                    if (mAccount.mCallbacks != null) {
                        mMainHandler.post(new Runnable() {
                            public void run() {
                                mAccount.mCallbacks.userRemotePasswordChange();
                            }
                        });
                    }
                }
            }
        });

        List<String> uuids = mAccount.walletIds();
        for (String uuid : uuids) {
            requestWalletDataSync(uuid);
        }

        mDataHandler.post(new Runnable() {
            public void run() {
                boolean pending = false;
                try {
                    pending = mApi.isOtpResetPending(mAccount.username());
                } catch (AirbitzException e) {
                    AirbitzCore.loge("mDataHandler.post error:");
                }
                final boolean isPending = pending;
                mMainHandler.post(new Runnable() {
                    public void run() {
                        if (!mDataFetched) {
                            mDataFetched = true;
                            connectWatchers();
                        }
                        if (isPending && mAccount.mCallbacks != null) {
                            mAccount.mCallbacks.otpResetPending();
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

                core.ABC_DataSyncWallet(mAccount.username(), mAccount.password(), uuid, dirty, error);
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

    private static final int BALANCE_CHANGE_DELAY = 300;

    private void callbackAsyncBitcoinInfo(long asyncBitCoinInfo_ptr) {
        tABC_AsyncBitCoinInfo info = Jni.newAsyncBitcoinInfo(asyncBitCoinInfo_ptr);
        tABC_AsyncEventType type = info.getEventType();

        AirbitzCore.logi("asyncBitCoinInfo callback type = "+type.toString());
        if (type==tABC_AsyncEventType.ABC_AsyncEventType_IncomingBitCoin) {
            mIncomingWallet = info.getSzWalletUUID();
            mIncomingTxId = info.getSzTxID();

            // Notify app of new tx
            mMainHandler.removeCallbacks(mIncomingBitcoinUpdater);
            mMainHandler.postDelayed(mIncomingBitcoinUpdater, BALANCE_CHANGE_DELAY);
        } else if (type == tABC_AsyncEventType.ABC_AsyncEventType_BlockHeightChange) {
            mMainHandler.post(mBlockHeightUpdater);
        } else if (type == tABC_AsyncEventType.ABC_AsyncEventType_AddressCheckDone) {
            mMainHandler.post(mNotifyBitcoinLoaded);
        } else if (type == tABC_AsyncEventType.ABC_AsyncEventType_BalanceUpdate) {
            mMainHandler.removeCallbacks(mBalanceUpdated);
            mMainHandler.postDelayed(mBalanceUpdated, BALANCE_CHANGE_DELAY);
        } else if (type == tABC_AsyncEventType.ABC_AsyncEventType_IncomingSweep) {
            final String uuid = info.getSzWalletUUID();
            final String txid = info.getSzTxID();
            final long amount = Jni.get64BitLongAtPtr(Jni.getCPtr(info.getSweepSatoshi()));
            if (mAccount.mCallbacks != null) {
                mMainHandler.postDelayed(new Runnable() {
                    public void run() {
                        final Wallet wallet = mAccount.wallet(uuid);
                        final Transaction tx = wallet.transaction(txid);
                        mAccount.mCallbacks.sweep(wallet, tx, amount);
                    }
                }, BALANCE_CHANGE_DELAY);
            }
        }
    }

    private Wallet getWalletFromCore(String uuid) {
        tABC_CC result;
        tABC_Error error = new tABC_Error();

        Wallet wallet = new Wallet(mAccount, uuid);
        if (null != mWatcherTasks.get(uuid)) {
            // Load Wallet name
            SWIGTYPE_p_long pName = core.new_longp();
            SWIGTYPE_p_p_char ppName = core.longp_to_ppChar(pName);
            result = core.ABC_WalletName(mAccount.username(), uuid, ppName, error);
            if (result == tABC_CC.ABC_CC_Ok) {
                wallet.name(Jni.getStringAtPtr(core.longp_value(pName)));
            }

            // Load currency
            SWIGTYPE_p_int pCurrency = core.new_intp();
            SWIGTYPE_p_unsigned_int upCurrency = core.int_to_uint(pCurrency);

            result = core.ABC_WalletCurrency(mAccount.username(), uuid, pCurrency, error);
            if (result == tABC_CC.ABC_CC_Ok) {
                wallet.mCurrencyNum = core.intp_value(pCurrency);
            } else {
                wallet.mCurrencyNum = -1;
            }
            wallet.mSynced = wallet.mCurrencyNum != -1;

            // Load balance
            SWIGTYPE_p_int64_t l = core.new_int64_tp();
            result = core.ABC_WalletBalance(mAccount.username(), uuid, l, error);
            if (result == tABC_CC.ABC_CC_Ok) {
                wallet.balance(Jni.get64BitLongAtPtr(Jni.getCPtr(l)));
            } else {
                wallet.balance(0);
            }
        }
        return wallet;
    }

}
