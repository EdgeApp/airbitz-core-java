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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import co.airbitz.internal.SWIGTYPE_p_int;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_TxDetails;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;
import co.airbitz.internal.tABC_TxDetails;

public class Transaction {
    private static String TAG = Transaction.class.getSimpleName();
    private static int CONFIRMED_CONFIRMATION_COUNT = 3;

    long mAmountFeesAirbitzSatoshi;
    long mAmountFeesMinersSatoshi;
    long mBizId;
    int mAttributes;
    private String mWalletUUID;
    private String mWalletName;
    private String mID;
    private String mMalleableID;
    private long mDate;
    private String mName;
    private String mAddress;
    private String mCategory;
    private String mNotes;
    private TxOutput[] mOutputs;
    private boolean mConfirmed;
    private boolean mSyncing;
    private int mConfirmations;
    private long mAmountSatoshi;
    private double mAmountFiat;
    private long mMinerFees;
    private long mABFees;
    private long mBalance;

    private Wallet mWallet;
    private Account mAccount;
    private TxInfo mTxInfo;

    Transaction(Account account, Wallet wallet, TxInfo txInfo) {
        mAccount = account;
        mWallet = wallet;
        mTxInfo = txInfo;

        mID = "";
        mMalleableID = "";
        mWalletUUID = "";
        mWalletName = "";
        mName = "";
        mAmountSatoshi = 0;
        mDate = System.currentTimeMillis();
        mCategory = "";
        mNotes = "";
        mAmountFeesAirbitzSatoshi = 0;
        mAmountFeesMinersSatoshi = 0;
        mBizId = 0;
        mAttributes = 0;
        setup();
    }

    public void setup() {
        setID(mTxInfo.getID());
        setName(mTxInfo.getDetails().getSzName());
        setNotes(mTxInfo.getDetails().getSzNotes());
        setCategory(mTxInfo.getDetails().getSzCategory());
        setmBizId(mTxInfo.getDetails().getBizId());
        setDate(mTxInfo.getCreationTime());

        setAmountSatoshi(mTxInfo.getDetails().getmAmountSatoshi());
        setABFees(mTxInfo.getDetails().getmAmountFeesAirbitzSatoshi());
        setMinerFees(mTxInfo.getDetails().getmAmountFeesMinersSatoshi());

        setAmountFiat(mTxInfo.getDetails().getmAmountCurrency());
        setWalletName(mWallet.getName());
        setWalletUUID(mWallet.getUUID());
        if (mTxInfo.getSzMalleableTxId()!=null) {
            setmMalleableID(mTxInfo.getSzMalleableTxId());
        }

        int confirmations = height();
        setConfirmations(confirmations);
        setConfirmed(false);
        setConfirmed(getConfirmations() >= CONFIRMED_CONFIRMATION_COUNT);
        if (!getName().isEmpty()) {
            setAddress(getName());
        } else {
            setAddress("");
        }

        if (!getName().isEmpty()) {
            setAddress(getName());
        } else {
            setAddress("");
        }
        TxOutput[] txo = mTxInfo.getOutputs();
        if (txo != null) {
            setOutputs(txo);
        }
    }

    public void save() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_sABC_TxDetails pDetails = core.longp_to_ppTxDetails(lp);

        core.ABC_GetTransactionDetails(
                mAccount.getUsername(), mAccount.getPassword(),
                mWallet.getUUID(), getID(), pDetails, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(null, error.getCode(), error);
        }

        tABC_TxDetails details = new TxDetails(core.longp_value(lp));
        details.setSzName(getName());
        details.setSzCategory(getCategory());
        details.setSzNotes(getNotes());
        details.setAmountCurrency(getAmountFiat());
        details.setBizId(getmBizId());

        error = new tABC_Error();
        core.ABC_SetTransactionDetails(
                mAccount.getUsername(), mAccount.getPassword(),
                mWallet.getUUID(), getID(), details, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(null, error.getCode(), error);
        }
        mAccount.sendReloadWallets();
    }

    public int height() {
        tABC_Error Error = new tABC_Error();
        SWIGTYPE_p_int th = core.new_intp();
        SWIGTYPE_p_int bh = core.new_intp();

        setSyncing(false);
        if (mWallet.getUUID().length() == 0 || getID().length() == 0) {
            return 0;
        }
        if (core.ABC_TxHeight(mWallet.getUUID(), getID(), th, Error) != tABC_CC.ABC_CC_Ok) {
            setSyncing(true);
            return 0;
        }
        if (core.ABC_BlockHeight(mWallet.getUUID(), bh, Error) != tABC_CC.ABC_CC_Ok) {
            setSyncing(true);
            return 0;
        }

        int txHeight = core.intp_value(th);
        int blockHeight = core.intp_value(bh);
        if (txHeight == 0 || blockHeight == 0) {
            return 0;
        }
        return (blockHeight - txHeight) + 1;
    }

    public String getWalletUUID() {
        return mWalletUUID;
    }

    public void setWalletUUID(String uuid) {
        this.mWalletUUID = uuid;
    }

    public String getWalletName() {
        return mWalletName;
    }

    public void setWalletName(String name) {
        this.mWalletName = name;
    }

    public String getID() {
        return mID;
    }

    public void setID(String mID) {
        this.mID = mID;
    }

    public String getmMalleableID() {
        return mMalleableID;
    }

    public void setmMalleableID(String mID) {
        this.mMalleableID = mID;
    }

    public long getDate() {
        return mDate;
    }

    public void setDate(long mDate) {
        this.mDate = mDate;
    }

    public String getName() {
        return mName;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    public String getAddress() {
        return mAddress;
    }

    public void setAddress(String mAddress) {
        this.mAddress = mAddress;
    }

    public String getCategory() {
        return mCategory;
    }

    public void setCategory(String mCategory) {
        this.mCategory = mCategory;
    }

    public String getNotes() {
        return mNotes;
    }

    public void setNotes(String mNotes) {
        this.mNotes = mNotes;
    }

    public TxOutput[] getOutputs() {
        return mOutputs;
    }

    public void setOutputs(TxOutput[] outputs) {
        this.mOutputs = outputs;
    }

    public boolean isConfirmed() {
        return mConfirmed;
    }

    public void setConfirmed(boolean mConfirmed) {
        this.mConfirmed = mConfirmed;
    }

    public boolean isSyncing() {
        return mSyncing;
    }

    public void setSyncing(boolean syncing) {
        this.mSyncing = syncing;
    }

    public int getConfirmations() {
        return mConfirmations;
    }

    public void setConfirmations(int mConfirmations) {
        this.mConfirmations = mConfirmations;
    }

    public int getAttributes() {
        return mAttributes;
    }

    public void setAttributes(int mAttributes) {
        this.mAttributes = mAttributes;
    }

    public long getAmountSatoshi() {
        return mAmountSatoshi;
    }

    public void setAmountSatoshi(long mAmountSatoshi) {
        this.mAmountSatoshi = mAmountSatoshi;
    }

    public double getAmountFiat() {
        return mAmountFiat;
    }

    public void setAmountFiat(double mAmountFiat) {
        this.mAmountFiat = mAmountFiat;
    }

    public long getMinerFees() {
        return mMinerFees;
    }

    public void setMinerFees(long mMinerFees) {
        this.mMinerFees = mMinerFees;
    }

    public long getABFees() {
        return mABFees;
    }

    public void setABFees(long mABFees) {
        this.mABFees = mABFees;
    }

    public long getBalance() {
        return mBalance;
    }

    public void setBalance(long mBalance) {
        this.mBalance = mBalance;
    }

    public long getmAmountFeesAirbitzSatoshi() {
        return mAmountFeesAirbitzSatoshi;
    }

    public void setmAmountFeesAirbitzSatoshi(long mAmountFeesAirbitzSatoshi) {
        this.mAmountFeesAirbitzSatoshi = mAmountFeesAirbitzSatoshi;
    }

    public long getmAmountFeesMinersSatoshi() {
        return mAmountFeesMinersSatoshi;
    }

    public void setmAmountFeesMinersSatoshi(long mAmountFeesMinersSatoshi) {
        this.mAmountFeesMinersSatoshi = mAmountFeesMinersSatoshi;
    }

    public long getmBizId() {
        return mBizId;
    }

    public void setmBizId(long mBizId) {
        this.mBizId = mBizId;
    }

}
