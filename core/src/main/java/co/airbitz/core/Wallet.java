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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_bool;
import co.airbitz.internal.SWIGTYPE_p_int64_t;
import co.airbitz.internal.SWIGTYPE_p_int;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_p_sABC_TxInfo;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_TxInfo;
import co.airbitz.internal.SWIGTYPE_p_unsigned_int;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

/**
 * Wallet represents a single HD, multiple address, wallet within an {@link
 * Account}.  This object is the basis for {@link Spend Spends} and {@link
 * ReceiveAddress Requests}. Initiate sends by calling {@link Wallet#newSpend
 * newSpend} which returns an {@link Spend} object.  Use {@link
 * Wallet#newReceiveRequest newReceiveRequest} to generate an {@link
 * ReceiveAddress} which contains a bitcoin address to receive funds.
 */
public class Wallet {
    private Account mAccount;
    private String mName;
    private String mId;
    int mCurrencyNum;
    long mBalanceSatoshi = 0;
    boolean mArchived = false;
    boolean mSynced = false;
    List<Transaction> mTransactions;

    Wallet(Account account, String uuid) {
        this.mAccount = account;
        this.mCurrencyNum = -1;
        this.mTransactions = new ArrayList<Transaction>();
        this.mId = uuid;
        setup();
    }

    private void setup() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool archived = Jni.newBool(Jni.getCPtr(lp));
        core.ABC_WalletArchived(mAccount.username(), mId, archived, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            mArchived = Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        } else {
            mArchived = false;
        }
    }

    /**
     * Determines if the wallet has finished syncing
     * @return true if the wallet has finished syncing
     */
    public boolean isSynced() {
        return mSynced;
    }

    /**
     * Determines if the wallet is archived
     * @return true if the wallet is archived
     */
    public boolean isArchived() {
        return mArchived;
    }

    /**
     * Returns the wallet id
     * @return the wallet id
     */
    public String id() {
        return mId;
    }

    /**
     * Internally only...
     */
    void setUUID(String uuid) {
        mId = uuid;
    }

    /**
     * Archives or un-archives the wallet.
     * @return true if the wallet was successfully archived.
     */
    public boolean walletArchived(boolean archived) {
        long attr = archived ? 1 : 0;
        tABC_Error error = new tABC_Error();
        core.ABC_SetWalletArchived(
                mAccount.username(), mAccount.password(),
                id(), attr, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            mArchived = archived;
            return true;
        }
        return false;
    }

    /**
     * Deletes wallet from user's account. This will render wallet completely
     * inaccessible including any future funds that may be sent to any
     * addresses in this wallet.
     * @return true if the wallet was successfully removed.
     */
    public boolean walletRemove() {
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_WalletRemove(mAccount.username(), id(), error);
        if (result == tABC_CC.ABC_CC_Ok) {
            mAccount.engine().stopWatcher(id());
            mAccount.reloadWallets();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Rename the wallet.
     * @param newName new name of wallet
     * @return true if the wallet was successfully renamed.
     */
    public boolean name(String newName) {
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_RenameWallet(
                mAccount.username(), mAccount.password(),
                id(), newName, error);
        if (result == tABC_CC.ABC_CC_Ok) {
            mName = newName;
            mAccount.reloadWallets();
        }
        return result == tABC_CC.ABC_CC_Ok;
    }

    /**
     * Retreive the wallet name.
     * @return the wallet name
     */
    public String name() {
        return mName;
    }

    /**
     * Only used internally.
     */
    protected void setName(String name) {
        this.mName = name;
    }

    /**
     * Retrieve the wallet currency.
     * @return the {@link CoreCurrency} object for the wallet's currency
     */
    public CoreCurrency currency() {
        return Currencies.instance().lookup(
            Currencies.instance().map(mCurrencyNum));
    }

    /**
     * Retrieve the wallet balance.
     * @return the wallet balance in satoshis.
     */
    public long balance() {
        return mBalanceSatoshi;
    }

    /**
     * Internal use only
     */
    void balance(long bal) {
        mBalanceSatoshi = bal;
    }

    /**
     * Export a wallet's private seed in raw entropy format.
     * @return the hex encoded private seed
     */
    public String seed() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        tABC_CC result = core.ABC_ExportWalletSeed(
                mAccount.username(), mAccount.password(),
                id(), ppChar, error);
        if (tABC_CC.ABC_CC_Ok == result) {
            return Jni.getStringAtPtr(core.longp_value(lp));
        } else {
            return null;
        }
    }

    /*
     * Export a wallet's public seed (XPub)
     * @return string of public seed contents.
     */
    public String xpub() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        tABC_CC result = core.ABC_ExportWalletXPub(
                mAccount.username(), mAccount.password(),
                id(), ppChar, error);
        if (tABC_CC.ABC_CC_Ok == result) {
            return Jni.getStringAtPtr(core.longp_value(lp));
        } else {
            return null;
        }
    }

    /**
     * Export a wallet's transactions to CSV format, for a given date range.
     * @param start timestamp of start export
     * @param end timestamp of end export
     * @return csv file contents
     */
    public String csvExport(long start, long end) {
        tABC_Error pError = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        SWIGTYPE_p_int64_t startTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(startTime), start); //0 means all transactions

        SWIGTYPE_p_int64_t endTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(endTime), end); //0 means all transactions

        tABC_CC result = core.ABC_CsvExport(
                mAccount.username(), mAccount.password(),
                id(), startTime, endTime, ppChar, pError);
        if (result == tABC_CC.ABC_CC_Ok) {
            return Jni.getStringAtPtr(core.longp_value(lp)); // will be null for NoRecoveryQuestions
        } else if (result == tABC_CC.ABC_CC_NoTransaction) {
            return "";
        } else {
            AirbitzCore.loge(pError.getSzDescription() +
                            ";" + pError.getSzSourceFile() +
                            ";" + pError.getSzSourceFunc() +
                            ";" + pError.getNSourceLine());
            return null;
        }
    }

    /**
     * Export a wallet's transactions to QBO format, for a given date range.
     * @param start timestamp of start export
     * @param end timestamp of end export
     * @return csv file contents
     */
    public String qboExport(long start, long end) {
        tABC_Error pError = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        SWIGTYPE_p_int64_t startTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(startTime), start); //0 means all transactions

        SWIGTYPE_p_int64_t endTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(endTime), end); //0 means all transactions

        tABC_CC result = core.ABC_QBOExport(
                mAccount.username(), mAccount.password(),
                id(), startTime, endTime, ppChar, pError);
        if (result == tABC_CC.ABC_CC_Ok) {
            return Jni.getStringAtPtr(core.longp_value(lp)); // will be null for NoRecoveryQuestions
        } else if (result == tABC_CC.ABC_CC_NoTransaction) {
            return "";
        } else {
            AirbitzCore.loge(pError.getSzDescription() +
                            ";" + pError.getSzSourceFile() +
                            ";" + pError.getSzSourceFunc() +
                            ";" + pError.getNSourceLine());
            return null;
        }
    }

    /**
     * Create a receive request from the current wallet.
     * @return the newly instantiated {@link ReceiveAddress}
     */
    public ReceiveAddress newReceiveRequest() {
        return new ReceiveAddress(mAccount, this);
    }

    /**
     * Load an existing receive request from the current wallet.
     * @return the newly instantiated {@link ReceiveAddress}
     * for an existing request.
     */
    public ReceiveAddress fetchReceiveRequest(String address) {
        return new ReceiveAddress(mAccount, this, address);
    }

    /**
     * Create a new {@link Spend} object.
     * @return newly instantiated {@link Spend} object.
     */
    public Spend newSpend() throws AirbitzException {
        return new Spend(mAccount, this);
    }

    /**
     * Fetch a transaction by txid.
     * @param txid the id of the transaction
     * @return the requested transaction or null if it cannot be found
     */
    public Transaction transaction(String txid) {
        if (mTransactions != null) {
            for (Transaction t : mTransactions) {
                if (t.id().equals(txid)) {
                    return t;
                }
            }
        }
        tABC_Error error = new tABC_Error();
        Transaction transaction = null;

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_sABC_TxInfo pTxInfo = core.longp_to_ppTxInfo(lp);

        tABC_CC result = core.ABC_GetTransaction(
                mAccount.username(), mAccount.password(),
                id(), txid, pTxInfo, error);
        if (result == tABC_CC.ABC_CC_Ok) {
            TxInfo txInfo = new TxInfo(core.longp_value(lp));
            transaction = new Transaction(mAccount, this, txInfo);
        } else {
            AirbitzCore.loge("Error: Wallet.transaction: "+ error.getSzDescription());
        }
        return transaction;
    }

    void loadTransactions() {
        List<Transaction> listTransactions = new ArrayList<Transaction>();
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int puCount = core.int_to_uint(pCount);

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_sABC_TxInfo paTxInfo = core.longp_to_pppTxInfo(lp);

        SWIGTYPE_p_int64_t startTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(startTime), 0); // 0 means all transactions

        SWIGTYPE_p_int64_t endTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(endTime), 0); // 0 means all transactions

        tABC_CC result = core.ABC_GetTransactions(
                mAccount.username(), mAccount.password(),
                id(), startTime, endTime, paTxInfo, puCount, error);

        if (result == tABC_CC.ABC_CC_Ok) {
            int ptrToInfo = core.longp_value(lp);
            int count = core.intp_value(pCount);
            Jni.ppTxInfo base = new Jni.ppTxInfo(ptrToInfo);

            for (int i = count-1; i >= 0 ; i--) {
                Jni.pLong temp = new Jni.pLong(base.getPtr(base, i * 4));
                TxInfo txi = new TxInfo(core.longp_value(temp));

                Transaction in = new Transaction(mAccount, this, txi);
                listTransactions.add(in);
            }

            core.ABC_FreeTransactions(new Jni.ppTxInfo(ptrToInfo), count);
            mTransactions = listTransactions;
        } else {
            AirbitzCore.loge("Error: CoreBridge.loadAllTransactions: "+ error.getSzDescription());
        }
    }

    /**
     * Fetch all transactions for this wallet
     * @return a list of {@link Transaction} objects
     */
    public List<Transaction> transactions() {
        return mTransactions;
    }

    /**
     * Search all transactions for this wallet
     * @return a list of {@link Transaction} objects matching the query.
     */
    public List<Transaction> transactionsSearch(String searchText) {
        List<Transaction> listTransactions = new ArrayList<Transaction>();
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int puCount = core.int_to_uint(pCount);

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_sABC_TxInfo paTxInfo = core.longp_to_pppTxInfo(lp);

        tABC_CC result = core.ABC_SearchTransactions(
                mAccount.username(), mAccount.password(),
                id(), searchText, paTxInfo, puCount, error);
        if (result == tABC_CC.ABC_CC_Ok) {
            int ptrToInfo = core.longp_value(lp);
            int count = core.intp_value(pCount);
            Jni.ppTxInfo base = new Jni.ppTxInfo(ptrToInfo);

            for (int i = count - 1; i >= 0; --i) {
                Jni.pLong temp = new Jni.pLong(base.getPtr(base, i * 4));
                long start = core.longp_value(temp);
                TxInfo txi = new TxInfo(start);

                Transaction transaction = new Transaction(mAccount, this, txi);
                listTransactions.add(transaction);
            }
        } else {
            AirbitzCore.loge("Error: CoreBridge.searchTransactionsIn: " + error.getSzDescription());
        }
        return listTransactions;
    }

    /**
     * Sweep a private key into this wallet. The method is asynchronous, and
     * will begin processing in the background once the input is validated.
     * @param wif the private key that will be swept.
     */
    public void sweepKey(String wif) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_SweepKey(mAccount.username(), mAccount.password(), id(), wif, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     * Requests that the wallet reconnects to new bitcoin servers. Useful
     * during a pull-to-refresh type of behavior so the user can try other
     * servers when waiting for data.
     */
    public void walletReconnect() {
        mAccount.engine().connectWatcher(id());
    }

    /**
     * Returns the current block height.
     * @return the current block height
     */
    public int blockHeight() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_int bh = core.new_intp();
        if (core.ABC_BlockHeight(id(), bh, error) != tABC_CC.ABC_CC_Ok) {
            return 0;
        }
        return core.intp_value(bh);
    }

    /**
     * Deprioritize all addresses.
     */
    public void deprioritize() {
        tABC_Error error = new tABC_Error();
        core.ABC_PrioritizeAddress(
                mAccount.username(), mAccount.password(),
                id(), null, error);
    }
}
