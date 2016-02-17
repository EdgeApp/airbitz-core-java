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

public class Wallet {
    private Account mAccount;
    private String mName;
    private String mUUID;
    private int mCurrencyNum;
    private long mAttributes;
    private long mBalanceSatoshi = 0;
    private List<Transaction> mTransactions;

    Wallet(Account account) {
        this.mAccount = account;
        this.mCurrencyNum = -1;
        this.mTransactions = new ArrayList<Transaction>();
    }

    public Transaction getTransaction(String txid) {
        tABC_Error error = new tABC_Error();
        Transaction transaction = null;

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_sABC_TxInfo pTxInfo = core.longp_to_ppTxInfo(lp);

        tABC_CC result = core.ABC_GetTransaction(
                mAccount.getUsername(), mAccount.getPassword(),
                this.getUUID(), txid, pTxInfo, error);
        if (result == tABC_CC.ABC_CC_Ok) {
            TxInfo txInfo = new TxInfo(core.longp_value(lp));
            transaction = new Transaction(mAccount, this, txInfo);
            core.ABC_FreeTransaction(txInfo);
        } else {
            AirbitzCore.debugLevel(1, "Error: CoreBridge.getTransaction: "+ error.getSzDescription());
        }
        return transaction;
    }

    public List<Transaction> loadTransactionsRange(long start, long end) {
        List<Transaction> listTransactions = new ArrayList<Transaction>();
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int puCount = core.int_to_uint(pCount);

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_sABC_TxInfo paTxInfo = core.longp_to_pppTxInfo(lp);

        SWIGTYPE_p_int64_t startTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(SWIGTYPE_p_int64_t.getCPtr(startTime), start); // 0 means all transactions

        SWIGTYPE_p_int64_t endTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(SWIGTYPE_p_int64_t.getCPtr(endTime), end); // 0 means all transactions

        tABC_CC result = core.ABC_GetTransactions(
                mAccount.getUsername(), mAccount.getPassword(),
                this.getUUID(), startTime, endTime, paTxInfo, puCount, error);

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
            long bal = 0;
            for (Transaction at : listTransactions) {
                bal += at.getAmountSatoshi();
                at.setBalance(bal);
            }

            core.ABC_FreeTransactions(new SWIGTYPE_p_p_sABC_TxInfo(ptrToInfo, false), count);
            mTransactions = listTransactions;
        } else {
            AirbitzCore.debugLevel(1, "Error: CoreBridge.loadAllTransactions: "+ error.getSzDescription());
        }
        return listTransactions;
    }

    public List<Transaction> loadAllTransactions() {
        return loadTransactionsRange(0, 0);
    }

    public List<Transaction> searchTransactionsIn(String searchText) {
        List<Transaction> listTransactions = new ArrayList<Transaction>();
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int puCount = core.int_to_uint(pCount);

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_sABC_TxInfo paTxInfo = core.longp_to_pppTxInfo(lp);

        tABC_CC result = core.ABC_SearchTransactions(
                mAccount.getUsername(), mAccount.getPassword(),
                this.getUUID(), searchText, paTxInfo, puCount, error);
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

    public boolean walletRemove() {
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_WalletRemove(mAccount.getUsername(), this.getUUID(), error);
        if (result == tABC_CC.ABC_CC_Ok) {
            mAccount.stopWatcher(this.getUUID());
            mAccount.reloadWallets();
            return true;
        } else {
            return false;
        }
    }

    public boolean walletRename(String name) {
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_RenameWallet(
                mAccount.getUsername(), mAccount.getPassword(),
                this.getUUID(), name, error);
        return result == tABC_CC.ABC_CC_Ok;
    }

    public boolean walletArchived(boolean archived) {
        if (archived) {
            setAttributes(1);
        } else {
            setAttributes(0);
        }
        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_SetWalletArchived(
                mAccount.getUsername(), mAccount.getPassword(),
                this.getUUID(), this.getAttributes(), error);
        if (result == tABC_CC.ABC_CC_Ok) {
            return true;
        }
        return false;
    }


    public String csvExport(long start, long end) {
        tABC_Error pError = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        SWIGTYPE_p_int64_t startTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(SWIGTYPE_p_int64_t.getCPtr(startTime), start); //0 means all transactions

        SWIGTYPE_p_int64_t endTime = core.new_int64_tp();
        Jni.set64BitLongAtPtr(SWIGTYPE_p_int64_t.getCPtr(endTime), end); //0 means all transactions

        tABC_CC result = core.ABC_CsvExport(
                mAccount.getUsername(), mAccount.getPassword(),
                getUUID(), startTime, endTime, ppChar, pError);
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

    public String getPrivateSeed() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        tABC_CC result = core.ABC_ExportWalletSeed(
                mAccount.getUsername(), mAccount.getPassword(),
                this.getUUID(), ppChar, error);
        if (tABC_CC.ABC_CC_Ok == result) {
            return Jni.getStringAtPtr(core.longp_value(lp));
        } else {
            return null;
        }
    }

    public String sweepKey(String wif) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        int result = Jni.coreSweepKey(
                mAccount.getUsername(), mAccount.getPassword(),
                getUUID(), wif, SWIGTYPE_p_p_char.getCPtr(ppChar), tABC_Error.getCPtr(error));
        if (result != 0) {
            return "";
        } else {
            return Jni.getStringAtPtr(core.longp_value(lp));
        }
    }

    public ReceiveAddress.Builder receiveRequestBuilders() {
        return new ReceiveAddress.Builder(mAccount, this);
    }

    public SpendTarget newSpendTarget() {
        return new SpendTarget(mAccount, this);
    }

    public long GetTotalSentToday() {
        Calendar beginning = Calendar.getInstance();
        long end = beginning.getTimeInMillis() / 1000;
        beginning.set(Calendar.HOUR_OF_DAY, 0);
        beginning.set(Calendar.MINUTE, 0);
        long start = beginning.getTimeInMillis() / 1000;

        long sum = 0;
        List<Transaction> list = loadTransactionsRange(start, end);
        for (Transaction tx : list) {
            if (tx.getAmountSatoshi() < 0) {
                sum -= tx.getAmountSatoshi();
            }
        }
        return sum;
    }

    public boolean isArchived() {
        return (getAttributes() & 0x1) == 1;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getUUID() {
        return mUUID;
    }

    public void setUUID(String uuid) {
        mUUID = uuid;
    }

    public int getCurrencyNum() {
        return mCurrencyNum;
    }

    public void setCurrencyNum(int num) {
        mCurrencyNum = num;
    }

    public boolean isLoading() {
        return mCurrencyNum == -1;
    }

    public long getAttributes() {
        return mAttributes;
    }

    public void setAttributes(long attr) {
        mAttributes = attr;
    }

    public long getBalanceSatoshi() {
        return mBalanceSatoshi;
    }

    public void setBalanceSatoshi(long bal) {
        mBalanceSatoshi = bal;
    }

    public List<Transaction> getTransactions() {
        return mTransactions;
    }

    public boolean finalizeRequest(String address) {
        tABC_Error error = new tABC_Error();
        core.ABC_FinalizeReceiveRequest(
                mAccount.getUsername(), mAccount.getPassword(),
                getUUID(), address, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }
}