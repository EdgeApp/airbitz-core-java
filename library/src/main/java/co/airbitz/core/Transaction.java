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
    private String mId;
    private String mMalId;
    private long mDate;
    private TxOutput[] mOutputs;
    private boolean mConfirmed;
    private boolean mSyncing;
    private int mConfirmations;
    private long mAmountSatoshi;
    private long mMinerFees;
    private long mABFees;

    private Wallet mWallet;
    private Account mAccount;
    private TxInfo mTxInfo;
    private MetadataSet mMeta;

    Transaction(Account account, Wallet wallet, TxInfo txInfo) {
        mAccount = account;
        mWallet = wallet;
        mTxInfo = txInfo;
        mMeta = new MetadataSet();
        setup();
    }

    public void setup() {
        mId = mTxInfo.getID();
        mMeta.name(mTxInfo.getDetails().getSzName());
        mMeta.notes(mTxInfo.getDetails().getSzNotes());
        mMeta.category(mTxInfo.getDetails().getSzCategory());
        mMeta.bizid(mTxInfo.getDetails().getBizId());
        mMeta.fiat(mTxInfo.getDetails().getmAmountCurrency());

        mDate = mTxInfo.getCreationTime();
        amount(mTxInfo.getDetails().getmAmountSatoshi());

        setABFees(mTxInfo.getDetails().getmAmountFeesAirbitzSatoshi());
        setMinerFees(mTxInfo.getDetails().getmAmountFeesMinersSatoshi());

        if (mTxInfo.getSzMalleableTxId() != null) {
            mMalId = mTxInfo.getSzMalleableTxId();
        }

        mConfirmations = height();
        mConfirmed = mConfirmations >= CONFIRMED_CONFIRMATION_COUNT;

        TxOutput[] txo = mTxInfo.getOutputs();
        if (txo != null) {
            mOutputs = txo;
        }
    }

    public void save() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_sABC_TxDetails pDetails = core.longp_to_ppTxDetails(lp);

        core.ABC_GetTransactionDetails(
                mAccount.getUsername(), mAccount.getPassword(),
                mWallet.id(), getID(), pDetails, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(null, error.getCode(), error);
        }

        tABC_TxDetails details = new TxDetails(core.longp_value(lp));
        details.setSzName(mMeta.name());
        details.setSzCategory(mMeta.category());
        details.setSzNotes(mMeta.notes());
        details.setAmountCurrency(mMeta.fiat());
        details.setBizId(mMeta.bizid());

        error = new tABC_Error();
        core.ABC_SetTransactionDetails(
                mAccount.getUsername(), mAccount.getPassword(),
                mWallet.id(), getID(), details, error);
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
        if (mWallet.id().length() == 0 || getID().length() == 0) {
            return 0;
        }
        if (core.ABC_TxHeight(mWallet.id(), getID(), th, Error) != tABC_CC.ABC_CC_Ok) {
            setSyncing(true);
            return 0;
        }
        if (core.ABC_BlockHeight(mWallet.id(), bh, Error) != tABC_CC.ABC_CC_Ok) {
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

    public String getID() {
        return mId;
    }

    public String malId() {
        return mMalId;
    }

    public MetadataSet meta() {
        return mMeta;
    }

    public long date() {
        return mDate;
    }

    public TxOutput[] outputs() {
        return mOutputs;
    }

    public boolean isConfirmed() {
        return mConfirmed;
    }

    public boolean isSyncing() {
        return mSyncing;
    }

    private void setSyncing(boolean syncing) {
        this.mSyncing = syncing;
    }

    public int confirmations() {
        return mConfirmations;
    }

    public long amount() {
        return mAmountSatoshi;
    }

    public void amount(long mAmountSatoshi) {
        this.mAmountSatoshi = mAmountSatoshi;
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
}
