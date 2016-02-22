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
        core.ABC_WalletArchived(mAccount.getUsername(), mId, archived, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            mArchived = Jni.get64BitLongAtPtr(Jni.getCPtr(lp)) == 1;
        } else {
            mArchived = false;
        }
    }

    public boolean isSynced() {
        return mSynced;
    }

    public boolean isArchived() {
        return mArchived;
    }

    public String id() {
        return mId;
    }

    /**
     * Internally only...
     */
    protected void setUUID(String uuid) {
        mId = uuid;
    }

    public boolean walletArchived(boolean archived) {
        long attr = archived ? 1 : 0;
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_SetWalletArchived(
                mAccount.getUsername(), mAccount.getPassword(),
                id(), attr, error);
        if (result == tABC_CC.ABC_CC_Ok) {
            mArchived = archived;
            return true;
        }
        return false;
    }

    public boolean walletRemove() {
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_WalletRemove(mAccount.getUsername(), id(), error);
        if (result == tABC_CC.ABC_CC_Ok) {
            mAccount.stopWatcher(id());
            mAccount.reloadWallets();
            return true;
        } else {
            return false;
        }
    }

    public boolean name(String name) {
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_RenameWallet(
                mAccount.getUsername(), mAccount.getPassword(),
                id(), name, error);
        if (result == tABC_CC.ABC_CC_Ok) {
            mName = name;
        }
        return result == tABC_CC.ABC_CC_Ok;
    }

    public String name() {
        return mName;
    }

    /**
     * Only used internally.
     */
    protected void setName(String name) {
        this.mName = name;
    }

    public String currencyCode() {
        return Currencies.instance().map(mCurrencyNum);
    }

    public void currencyCode(String code) {
        mCurrencyNum = Currencies.instance().map(code);
    }

    public long balance() {
        return mBalanceSatoshi;
    }

    public void balance(long bal) {
        mBalanceSatoshi = bal;
    }

    public String seed() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        tABC_CC result = core.ABC_ExportWalletSeed(
                mAccount.getUsername(), mAccount.getPassword(),
                id(), ppChar, error);
        if (tABC_CC.ABC_CC_Ok == result) {
            return Jni.getStringAtPtr(core.longp_value(lp));
        } else {
            return null;
        }
    }

    public String csvExport(long start, long end) {
        tABC_Error pError = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        SWIGTYPE_p_int64_t startTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(startTime), start); //0 means all transactions

        SWIGTYPE_p_int64_t endTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(endTime), end); //0 means all transactions

        tABC_CC result = core.ABC_CsvExport(
                mAccount.getUsername(), mAccount.getPassword(),
                id(), startTime, endTime, ppChar, pError);
        if (result == tABC_CC.ABC_CC_Ok) {
            return Jni.getStringAtPtr(core.longp_value(lp)); // will be null for NoRecoveryQuestions
        } else if (result == tABC_CC.ABC_CC_NoTransaction) {
            return "";
        } else {
            AirbitzCore.debugLevel(1, pError.getSzDescription() +
                            ";" + pError.getSzSourceFile() +
                            ";" + pError.getSzSourceFunc() +
                            ";" + pError.getNSourceLine());
            return null;
        }
    }

    public ReceiveAddress.Builder receiveRequestBuilders() {
        return new ReceiveAddress.Builder(mAccount, this);
    }

    public SpendTarget newSpendTarget() {
        return new SpendTarget(mAccount, this);
    }


    public Transaction getTransaction(String txid) {
        tABC_Error error = new tABC_Error();
        Transaction transaction = null;

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_sABC_TxInfo pTxInfo = core.longp_to_ppTxInfo(lp);

        tABC_CC result = core.ABC_GetTransaction(
                mAccount.getUsername(), mAccount.getPassword(),
                id(), txid, pTxInfo, error);
        if (result == tABC_CC.ABC_CC_Ok) {
            TxInfo txInfo = new TxInfo(core.longp_value(lp));
            transaction = new Transaction(mAccount, this, txInfo);
            core.ABC_FreeTransaction(txInfo);
        } else {
            AirbitzCore.debugLevel(1, "Error: CoreBridge.getTransaction: "+ error.getSzDescription());
        }
        return transaction;
    }

    public List<Transaction> transactions() {
        return transactions(0, 0);
    }

    public List<Transaction> transactions(long start, long end) {
        List<Transaction> listTransactions = new ArrayList<Transaction>();
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int puCount = core.int_to_uint(pCount);

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_sABC_TxInfo paTxInfo = core.longp_to_pppTxInfo(lp);

        SWIGTYPE_p_int64_t startTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(startTime), start); // 0 means all transactions

        SWIGTYPE_p_int64_t endTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(endTime), end); // 0 means all transactions

        tABC_CC result = core.ABC_GetTransactions(
                mAccount.getUsername(), mAccount.getPassword(),
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
            AirbitzCore.debugLevel(1, "Error: CoreBridge.loadAllTransactions: "+ error.getSzDescription());
        }
        return listTransactions;
    }

    public List<Transaction> transactionsSearch(String searchText) {
        List<Transaction> listTransactions = new ArrayList<Transaction>();
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int puCount = core.int_to_uint(pCount);

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_sABC_TxInfo paTxInfo = core.longp_to_pppTxInfo(lp);

        tABC_CC result = core.ABC_SearchTransactions(
                mAccount.getUsername(), mAccount.getPassword(),
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
            AirbitzCore.debugLevel(1, "Error: CoreBridge.searchTransactionsIn: " + error.getSzDescription());
        }
        return listTransactions;
    }

    public String sweepKey(String wif) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        int result = Jni.coreSweepKey(
                mAccount.getUsername(), mAccount.getPassword(),
                id(), wif, Jni.getCPtr(ppChar), Jni.getCPtr(error));
        if (result != 0) {
            return "";
        } else {
            return Jni.getStringAtPtr(core.longp_value(lp));
        }
    }

    public boolean finalizeRequest(String address) {
        tABC_Error error = new tABC_Error();
        core.ABC_FinalizeReceiveRequest(
                mAccount.getUsername(), mAccount.getPassword(),
                id(), address, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }
}
