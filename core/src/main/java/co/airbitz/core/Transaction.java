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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import co.airbitz.internal.SWIGTYPE_p_int;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_TxDetails;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;
import co.airbitz.internal.tABC_TxDetails;

/**
 * Transaction contains information about a bitcoin transaction. In addition to
 * inputs/outputs and miner fees, but also metadata set by the user.
 */
public class Transaction {
    private static String TAG = Transaction.class.getSimpleName();

    long mAmountFeesAirbitzSatoshi;
    long mAmountFeesMinersSatoshi;
    private String mId;
    private Date mDate;
    private List<TxOutput> mOutputs;
    private List<TxOutput> mInputs;
    private boolean mSyncing;
    private long mAmountSatoshi;
    private long mMinerFees;
    private long mABFees;
    private int mHeight;
    private boolean mDoubleSpend;
    private boolean mReplaceByFee;

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
        mHeight = (int) mTxInfo.getHeight();
        mDoubleSpend = mTxInfo.getBDoubleSpent();
        mReplaceByFee = mTxInfo.getBReplaceByFee();

        mMeta.name(mTxInfo.getDetails().getSzName());
        mMeta.notes(mTxInfo.getDetails().getSzNotes());
        mMeta.category(mTxInfo.getDetails().getSzCategory());
        mMeta.bizid(mTxInfo.getDetails().getBizId());
        mMeta.fiat(mTxInfo.getDetails().getmAmountCurrency());

        mDate = new Date(mTxInfo.getCreationTime() * 1000);
        amount(mTxInfo.getDetails().getmAmountSatoshi());

        mABFees = mTxInfo.getDetails().getmAmountFeesAirbitzSatoshi();
        mMinerFees = mTxInfo.getDetails().getmAmountFeesMinersSatoshi();

        mInputs = new ArrayList<TxOutput>();
        mOutputs = new ArrayList<TxOutput>();

        TxOutput[] txo = mTxInfo.getOutputs();
        if (txo != null) {
            for (TxOutput t : txo) {
                if (t.isInput()) {
                    mInputs.add(t);
                } else {
                    mOutputs.add(t);
                }
            }
        }
    }

    /**
     * Save the transaction and write the meta data to disk.
     */
    public void save() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_sABC_TxDetails pDetails = core.longp_to_ppTxDetails(lp);

        core.ABC_GetTransactionDetails(
                mAccount.username(), mAccount.password(),
                mWallet.id(), id(), pDetails, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }

        tABC_TxDetails details = new TxDetails(core.longp_value(lp));
        details.setSzName(mMeta.name());
        details.setSzCategory(mMeta.category());
        details.setSzNotes(mMeta.notes());
        details.setAmountCurrency(mMeta.fiat());
        details.setBizId(mMeta.bizid());

        error = new tABC_Error();
        core.ABC_SetTransactionDetails(
                mAccount.username(), mAccount.password(),
                mWallet.id(), id(), details, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
        mAccount.sendReloadWallets();
    }

    /**
     * Retrieve the block height of the transaction. This is the block that the
     * transaction was mined in.
     */
    public int height() {
        if (mHeight != 0) {
            return mHeight;
        }

        tABC_Error Error = new tABC_Error();
        SWIGTYPE_p_int th = core.new_intp();
        if (core.ABC_TxHeight(mWallet.id(), id(), th, Error) != tABC_CC.ABC_CC_Ok) {
            return 0;
        }
        mHeight = core.intp_value(th);
        return mHeight;
    }

    /**
     * Retrieve the transaction id.
     * @return the transaction id
     */
    public String id() {
        return mId;
    }

    /**
     * Retrieve the metadata object
     * @return the metadata object
     */
    public MetadataSet meta() {
        return mMeta;
    }

    /**
     * Retrieve the date of the transaction
     * @return the date of the transaction
     */
    public Date date() {
        return mDate;
    }

    /**
     * Retrieve the outputs of the transaction
     * @return the outputs of the transaction
     */
    public List<TxOutput> outputs() {
        return mOutputs;
    }

    /**
     * Retrieve the inputs of the transaction
     * @return the inputs of the transaction
     */
    public List<TxOutput> inputs() {
        return mInputs;
    }

    /**
     * Retrieve if this transaction is still syncing
     * @return true if this transaction is still syncing
     */
    public boolean isSyncing() {
        return mSyncing;
    }

    private void setSyncing(boolean syncing) {
        this.mSyncing = syncing;
    }

    /**
     * Retrieve the amount of this transaction
     * @return the amount of this transaction in satoshis
     */
    public long amount() {
        return mAmountSatoshi;
    }

    private void amount(long mAmountSatoshi) {
        this.mAmountSatoshi = mAmountSatoshi;
    }

    /**
     * Retrieve the miner fees of this transaction
     * @return the miner fees of this transaction in satoshis
     */
    public long minerFees() {
        return mMinerFees;
    }

    /**
     * Retrieve the provider fees of this transaction.
     * @return the provider fees of this transaction in satoshis
     */
    public long providerFees() {
        return mABFees;
    }

    /**
     * Retrieve if this is a double spend
     * @return true if this is a double spend
     */
    public boolean isDoubleSpend() {
        return mDoubleSpend;
    }

    /**
     * Retrieve if this is an RBF transaction
     * @return true if this is an RBF transaction
     */
    public boolean isReplaceByFee() {
        return mReplaceByFee;
    }
}
