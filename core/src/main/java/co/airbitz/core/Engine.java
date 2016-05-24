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
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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

class Engine {
    private static String TAG = Account.class.getSimpleName();

    private static int ABC_EXCHANGE_RATE_REFRESH_INTERVAL_SECONDS = 60;
    private static int ABC_SYNC_REFRESH_INTERVAL_SECONDS = 30;
    private static final int TX_LOADED_DELAY = 1000 * 20;

    public native boolean registerAsyncCallback();

    private AirbitzCore mApi;
    private Account mAccount;

    private ScheduledExecutorService mMainHandler;
    private ScheduledExecutorService mReloadExecutor;
    private ScheduledExecutorService mCoreHandler;
    private ScheduledExecutorService mWatcherExecutor;
    private ScheduledExecutorService mDataExecutor;
    private ScheduledExecutorService mExchangeExecutor;
    private ScheduledFuture mDataFuture;
    private ScheduledFuture mExchangeFuture;
    private ScheduledFuture mMainIncomingFuture;
    private ScheduledFuture mMainDataFuture;
    private boolean mDataFetched = false;

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

    private void sendIfNotEmptying(ExecutorService executor, Runnable runnable) {
        if (executor != null && !executor.isShutdown()) {
            executor.submit(runnable);
        } else {
            AirbitzCore.logi("Ignore message...handler is empting");
        }
    }

    private void startWatcher(final String uuid) {
        sendIfNotEmptying(mWatcherExecutor, new Runnable() {
            public void run() {
                if (uuid != null && !mWatcherTasks.containsKey(uuid)) {
                    tABC_Error error = new tABC_Error();
                    core.ABC_WatcherStart(mAccount.username(), mAccount.password(), uuid, error);
                    Utils.printABCError(error);
                    AirbitzCore.logi("Started watcher for " + uuid);

                    Thread thread = new Thread(new WatcherRunnable(uuid));
                    thread.start();

                    if (mDataFetched) {
                        connectWatcher(uuid);
                    }
                    mWatcherTasks.put(uuid, thread);

                    // Request a data sync as soon as watcher is started
                    requestWalletDataSync(uuid);
                    sendReloadWallets();
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
        sendIfNotEmptying(mWatcherExecutor, new Runnable() {
            public void run() {
                if (uuid != null && mWatcherTasks.containsKey(uuid) && mAccount.isLoggedIn()) {
                    AirbitzCore.logi("Watcher connecting  " + uuid + ".");
                    tABC_Error error = new tABC_Error();
                    core.ABC_WatcherConnect(uuid, error);
                    Utils.printABCError(error);
                } else {
                    AirbitzCore.logi("Watcher not connecting  " + uuid + ". Watcher not running.");
                }
            }
        });
    }

    public void disconnectWatchers() {
        sendIfNotEmptying(mWatcherExecutor, new Runnable() {
            public void run() {
                for (String uuid : mWatcherTasks.keySet()) {
                    tABC_Error error = new tABC_Error();
                    core.ABC_WatcherDisconnect(uuid, error);
                }
            }
        });
    }

    public void waitOnWatchers() {
        mWatcherExecutor.shutdown();
        while (mWatcherExecutor != null && !mWatcherExecutor.isTerminated()) {
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
        sendIfNotEmptying(mWatcherExecutor, new Runnable() {
            public void run() {
                tABC_Error error = new tABC_Error();
                List<String> uuids = new ArrayList<String>(mWatcherTasks.keySet());
                for (String uuid : uuids) {
                    core.ABC_WatcherStop(uuid, error);
                }
                // Wait for all of the threads to finish.
                for (String uuid : uuids) {
                    Thread t = mWatcherTasks.get(uuid);
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        AirbitzCore.loge(e.getMessage());
                    }
                    mWatcherTasks.remove(uuid);
                }
                for (String uuid : uuids) {
                    core.ABC_WatcherDelete(uuid, error);
                }
            }
        });
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
        sendIfNotEmptying(mMainHandler, new Runnable() {
            public void run() {
                reloadWallets();
            }
        });
    }

    private Future mReloadFuture = null;
    public void reloadWallets() {
        if (mReloadExecutor != null
                && (mReloadFuture == null || mReloadFuture.isDone())
                && mAccount.isLoggedIn()) {
            mReloadFuture = mReloadExecutor.submit(new Runnable() {
                public void run() {
                    List<Wallet> wallets = new ArrayList<Wallet>();
                    List<String> uuids = mAccount.walletIds();
                    for (String uuid : uuids) {
                        wallets.add(getWalletFromCore(uuid));
                    }
                    postWalletsToMain(wallets);
                }
            });
        }
    }

    private void postWalletsToMain(final List<Wallet> wallets) {
        sendIfNotEmptying(mMainHandler, new Runnable() {
            public void run() {
                mAccount.updateWallets(wallets);
                if (mAccount.mCallbacks != null) {
                    mAccount.mCallbacks.walletsChanged();
                }
            }
        });
    }

    String mIncomingWallet;
    String mIncomingTxId;

    final Runnable mIncomingBitcoinUpdater = new Runnable() {
        public void run() {
            if (mAccount.mCallbacks != null) {
                Wallet wallet = mAccount.wallet(mIncomingWallet);
                if (wallet != null) {
                    Transaction tx = wallet.transaction(mIncomingTxId);
                    mAccount.mCallbacks.incomingBitcoin(wallet, tx);
                }
            }
            mIncomingWallet = null;
            mIncomingTxId = null;
            reloadWallets();
        }
    };

    private void receiveDataSyncUpdate() {
        if (mMainDataFuture != null) {
            mMainDataFuture.cancel(false);
        }
        mMainDataFuture = mMainHandler.schedule(new Runnable() {
            public void run() {
                mAccount.mSettings = null;
                startWatchers();
                reloadWallets();
                if (mAccount.mCallbacks != null) {
                    mAccount.mCallbacks.accountChanged();
                }
            }
        }, ABC_EXCHANGE_RATE_REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void start() {
        mMainHandler = Executors.newScheduledThreadPool(1);
        mReloadExecutor = Executors.newScheduledThreadPool(1);
        mDataExecutor = Executors.newScheduledThreadPool(1);
        mExchangeExecutor = Executors.newScheduledThreadPool(1);
        mCoreHandler = Executors.newScheduledThreadPool(1);
        mWatcherExecutor = Executors.newScheduledThreadPool(1);

        final List<String> uuids = mAccount.walletIds();
        final int walletCount = uuids.size();
        if (mAccount.mCallbacks != null) {
            mMainHandler.submit(new Runnable() {
                public void run() {
                    mAccount.mCallbacks.walletsLoading();
                }
            });
        }
        for (final String uuid : uuids) {
            mCoreHandler.submit(new Runnable() {
                public void run() {
                    tABC_Error error = new tABC_Error();
                    core.ABC_WalletLoad(mAccount.username(), uuid, error);

                    startWatcher(uuid);
                    mMainHandler.submit(new Runnable() {
                        public void run() {
                            if (mAccount.mCallbacks != null) {
                                final Wallet wallet = mAccount.wallet(uuid);
                                mAccount.mCallbacks.walletChanged(wallet);
                            }
                        }
                    });
                    sendReloadWallets();
                }
            });
        }
        mCoreHandler.submit(new Runnable() {
            public void run() {
                startExchangeRateUpdates();
                startFileSyncUpdates();

                sendReloadWallets();
            }
        });
    }

    public void stop() {
        if (mCoreHandler == null
                || mDataExecutor == null
                || mExchangeExecutor == null
                || mWatcherExecutor == null
                || mMainHandler == null) {
            return;
        }
        stopWatchers();
        stopExchangeRateUpdates();
        stopFileSyncUpdates();

        mCoreHandler.shutdownNow();
        mDataExecutor.shutdownNow();
        mExchangeExecutor.shutdownNow();
        mReloadExecutor.shutdownNow();
        mMainHandler.shutdownNow();
        mWatcherExecutor.shutdown();
        while (!mCoreHandler.isTerminated()
                || !mExchangeExecutor.isTerminated()
                || !mWatcherExecutor.isTerminated()
                || !mReloadExecutor.isTerminated()
                || !mMainHandler.isTerminated()) {
            try {
                AirbitzCore.logi(
                    "Data: " + mDataExecutor.isTerminated() + ", " +
                    "Core: " + mCoreHandler.isTerminated() + ", " +
                    "Reload: " + mReloadExecutor.isTerminated() + ", " +
                    "Main: " + mMainHandler.isTerminated() + ", " +
                    "Exchange: " + mExchangeExecutor.isTerminated() + "");
                Thread.sleep(200);
            } catch (Exception e) {
                AirbitzCore.loge(e.getMessage());
            }
        }
    }

    void resume() {
        connectWatchers();
        startExchangeRateUpdates();
        startFileSyncUpdates();
    }

    void pause() {
        stopExchangeRateUpdates();
        stopFileSyncUpdates();
        disconnectWatchers();
    }

    void restoreConnectivity() {
        resume();
    }

    void lostConnectivity() {
        pause();
    }

    public void stopExchangeRateUpdates() {
        if (null != mExchangeExecutor) {
            mExchangeExecutor.shutdownNow();
            mExchangeExecutor = Executors.newScheduledThreadPool(1);
        }
    }

    public void startExchangeRateUpdates() {
        updateExchangeRates();
    }

    private void queueExchangeRateUpdate() {
        mMainHandler.submit(new Runnable() {
            public void run() {
                updateExchangeRates();
            }
        });
    }

    public void updateExchangeRates() {
        AirbitzCore.logi("updateExchangeRates");
        if ((mExchangeFuture != null && !mExchangeFuture.isDone())
                || mExchangeExecutor.isShutdown()) {
            return;
        }
        List<Wallet> wallets = mAccount.wallets();
        if (mAccount.isLoggedIn()
                && null != mAccount.settings()
                && null != wallets) {

            requestExchangeRateUpdate(mAccount, mAccount.settings().currency().code);
            for (Wallet wallet : wallets) {
                if (wallet.isSynced()) {
                    requestExchangeRateUpdate(mAccount, wallet.currency().code);
                }
            }
            if (mAccount.mCallbacks != null) {
                mMainHandler.submit(new Runnable() {
                    public void run() {
                        mAccount.mCallbacks.exchangeRateChanged();
                    }
                });
            }
        }
        mExchangeFuture = mExchangeExecutor.schedule(new Runnable() {
            public void run() {
                AirbitzCore.logi("Schedule mExchangeExecutor");
                queueExchangeRateUpdate();
            }
        }, ABC_EXCHANGE_RATE_REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    void requestExchangeRateUpdate(final Account account, final String currency) {
        mExchangeExecutor.submit(new Runnable() {
            public void run() {
                AirbitzCore.getApi().exchangeCache().update(account, currency);
            }
        });
    }

    public void stopFileSyncUpdates() {
        if (null != mDataExecutor) {
            mDataExecutor.shutdownNow();
            mDataExecutor = Executors.newScheduledThreadPool(1);
        }
    }

    public void startFileSyncUpdates() {
        syncAllData();
    }

    private void queueSyncAllData() {
        mMainHandler.submit(new Runnable() {
            public void run() {
                syncAllData();
            }
        });
    }

    public void syncAllData() {
        AirbitzCore.logi("syncAllData");
        if ((mDataFuture != null && !mDataFuture.isDone())
                || mDataExecutor.isShutdown()) {
            return;
        }
        mDataExecutor.submit(new Runnable() {
            public void run() {
                mApi.generalInfoUpdate();
            }
        });
        mDataExecutor.submit(new Runnable() {
            public void run() {
                tABC_Error error = new tABC_Error();
                SWIGTYPE_p_long pdirty = core.new_longp();
                SWIGTYPE_p_bool dirty = Jni.newBool(Jni.getCPtr(pdirty));
                SWIGTYPE_p_long pchange = core.new_longp();
                SWIGTYPE_p_bool passwordChange = Jni.newBool(Jni.getCPtr(pchange));

                core.ABC_DataSyncAccount(mAccount.username(), mAccount.password(), dirty, passwordChange, error);
                if (error.getCode() == tABC_CC.ABC_CC_InvalidOTP) {
                    final AirbitzException e = new AirbitzException(error.getCode(), error);
                    if (mAccount.isLoggedIn() && mAccount.mCallbacks != null) {
                        mMainHandler.submit(new Runnable() {
                            public void run() {
                                // if the account has an OTP token, then its probably a skew problem
                                if (mAccount.otpSecret() != null) {
                                    mAccount.mCallbacks.otpSkew();
                                } else {
                                    mAccount.mCallbacks.otpRequired();
                                }
                            }
                        });
                    }
                } else if (Jni.getBytesAtPtr(Jni.getCPtr(pdirty), 1)[0] != 0) {
                    // Data changed remotel
                    receiveDataSyncUpdate();
                } else if (Jni.getBytesAtPtr(Jni.getCPtr(pchange), 1)[0] != 0) {
                    if (mAccount.mCallbacks != null) {
                        mMainHandler.submit(new Runnable() {
                            public void run() {
                                mAccount.mCallbacks.remotePasswordChange();
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

        mDataExecutor.submit(new Runnable() {
            public void run() {
                boolean pending = false;
                try {
                    pending = mApi.isOtpResetPending(mAccount.username());
                } catch (AirbitzException e) {
                    AirbitzCore.loge("mDataExecutor.post error:");
                }
                final boolean isPending = pending;
                mMainHandler.submit(new Runnable() {
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
        mDataFuture = mDataExecutor.schedule(new Runnable() {
            public void run() {
                AirbitzCore.logi("Schedule mDataExecutor");
                queueSyncAllData();
            }
        }, ABC_SYNC_REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void requestWalletDataSync(final String uuid) {
        mDataExecutor.submit(new Runnable() {
            public void run() {
                tABC_Error error = new tABC_Error();
                SWIGTYPE_p_long pdirty = core.new_longp();
                SWIGTYPE_p_bool dirty = Jni.newBool(Jni.getCPtr(pdirty));

                core.ABC_DataSyncWallet(mAccount.username(), mAccount.password(), uuid, dirty, error);
                mMainHandler.submit(new Runnable() {
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

    private static final int BALANCE_CHANGE_DELAY_SECONDS = 1;
    // Hopefully AddressDone will fire before this does
    private static final int BLOCKCHAIN_WAIT = 1000 * 60;

    private void callbackAsyncBitcoinInfo(long asyncBitCoinInfo_ptr) {
        tABC_AsyncBitCoinInfo info = Jni.newAsyncBitcoinInfo(asyncBitCoinInfo_ptr);
        tABC_AsyncEventType type = info.getEventType();

        AirbitzCore.logi("asyncBitCoinInfo callback type = " + type.toString());
        if (type==tABC_AsyncEventType.ABC_AsyncEventType_IncomingBitCoin) {
            mIncomingWallet = info.getSzWalletUUID();
            mIncomingTxId = info.getSzTxID();

            // Notify app of new tx
            if (mMainIncomingFuture != null) {
                mMainIncomingFuture.cancel(false);
            }
            mMainHandler.schedule(mIncomingBitcoinUpdater, BALANCE_CHANGE_DELAY_SECONDS, TimeUnit.SECONDS);
        } else if (type == tABC_AsyncEventType.ABC_AsyncEventType_BlockHeightChange) {
            mMainHandler.submit(new Runnable() {
                public void run() {
                    mAccount.mSettings = null;
                    if (mAccount.mCallbacks != null) {
                        mAccount.mCallbacks.blockHeightChanged();
                    }
                }
            });
        } else if (type == tABC_AsyncEventType.ABC_AsyncEventType_AddressCheckDone) {
            List<String> ids = mAccount.walletIds();
            List<Wallet> wallets = mAccount.wallets();
            int walletCount = ids != null ? ids.size() : 0;
            int loadedCount = 0;
            if (wallets != null) {
                for (Wallet w : wallets) {
                    if (w.isSynced()) {
                        loadedCount++;
                    }
                }
            }
            // Check to see if all the wallets have finished sync-ing before notifying...
            if (walletCount == loadedCount) {
                mMainHandler.submit(new Runnable() {
                    public void run() {
                        if (mAccount.mCallbacks != null) {
                            mAccount.mCallbacks.walletsLoaded();
                        }
                    }
                });
            }
        } else if (type == tABC_AsyncEventType.ABC_AsyncEventType_BalanceUpdate) {
            final String uuid = info.getSzWalletUUID();
            final String txid = info.getSzTxID();
            if (mAccount.mCallbacks != null) {
                mMainHandler.schedule(new Runnable() {
                    public void run() {
                        final Wallet wallet = mAccount.wallet(uuid);
                        final Transaction tx = wallet.transaction(txid);
                        mAccount.mCallbacks.balanceUpdate(wallet, tx);
                        reloadWallets();
                    }
                }, BALANCE_CHANGE_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        } else if (type == tABC_AsyncEventType.ABC_AsyncEventType_IncomingSweep) {
            final String uuid = info.getSzWalletUUID();
            final String txid = info.getSzTxID();
            final long amount = Jni.get64BitLongAtPtr(Jni.getCPtr(info.getSweepSatoshi()));
            if (mAccount.mCallbacks != null) {
                mMainHandler.schedule(new Runnable() {
                    public void run() {
                        final Wallet wallet = mAccount.wallet(uuid);
                        Transaction tx = null;
                        if (txid != null && !"".equals(txid.trim())) {
                            tx = wallet.transaction(txid);
                        }
                        mAccount.mCallbacks.sweep(wallet, tx, amount);
                        reloadWallets();
                    }
                }, BALANCE_CHANGE_DELAY_SECONDS, TimeUnit.SECONDS);
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
                wallet.setName(Jni.getStringAtPtr(core.longp_value(pName)));
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
                wallet.loadTransactions();
            } else {
                wallet.balance(0);
            }
        }
        return wallet;
    }

}
