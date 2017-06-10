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

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_void;
import co.airbitz.internal.SWIGTYPE_p_uint64_t;
import co.airbitz.internal.SWIGTYPE_p_void;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;
import co.airbitz.internal.tABC_SpendFeeLevel;

/**
 * Spend is used to build a Spend from the {@link Wallet}. The caller can add
 * multiple spend targets by calling either of {@link #addAddress addAddress},
 * {@link #addTransfer addTransfer}, or {@link #addPaymentRequest
 * addPaymentRequest} repeated times. Use {@link #signBroadcastSave
 * signBroadcastSave} to send the transaction to the blockchain. This spend may
 * also be signed without broadcast by calling {@link #sign sign}.
 */
public class Spend {
    SWIGTYPE_p_void mSpend;
    SWIGTYPE_p_long _pl;
    SWIGTYPE_p_p_void _ppv;

    public enum FeeLevel {
        LOW(tABC_SpendFeeLevel.ABC_SpendFeeLevelLow),
        STANDARD(tABC_SpendFeeLevel.ABC_SpendFeeLevelStandard),
        HIGH(tABC_SpendFeeLevel.ABC_SpendFeeLevelHigh),
        CUSTOM(tABC_SpendFeeLevel.ABC_SpendFeeLevelCustom);

        private final tABC_SpendFeeLevel value;
        FeeLevel(tABC_SpendFeeLevel value) {
            this.value = value;
        }
    };

    private Account mAccount;
    private Wallet mWallet;
    private MetadataSet mMeta;
    private boolean mIsTransfer;
    private FeeLevel mFeeLevel;

    Spend(Account account, Wallet wallet) throws AirbitzException {
        mAccount = account;
        mWallet = wallet;
        mMeta = new MetadataSet();
        mMeta.mChangeListener = new MetadataSet.OnChangeListener() {
            public void onChange() {
                updateMeta();
            }
        };

        tABC_Error error = new tABC_Error();
        _pl = core.new_longp();
        _ppv = core.longp_to_ppvoid(_pl);
        core.ABC_SpendNew(mAccount.username(), mWallet.id(), _ppv, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
        mSpend = Jni.newSWIGTYPE_p_void(core.longp_value(_pl));
        mIsTransfer = false;
    }

    @Override
    protected void finalize() throws Throwable {
        if (mSpend != null) {
            core.ABC_SpendFree(mSpend);
        }
        super.finalize();
    }

    /**
     * Access the meta data for this Spend
     * @return the metadata object
     */
    public MetadataSet meta() {
        return mMeta;
    }

    /**
     * Indicates if this is a transfer
     * @return true if this is a transfer
     */
    public boolean isTransfer() {
        return mIsTransfer;
    }

    /**
     * Adds an address and amount to this spend request
     * @param address public address to send funds to
     * @param amount amount of bitcoin to send in satoshis
     */
    public void addAddress(String address, long amount) throws AirbitzException {
        SWIGTYPE_p_uint64_t ua = core.new_uint64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(ua), amount);

        tABC_Error error = new tABC_Error();
        core.ABC_SpendAddAddress(mSpend, address, ua, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     * Adds a BIP70 payment request to this Spend. No amount parameter is
     * provided as the payment request always has the amount included. Generate
     * an {@link PaymentRequest} object by calling {@link AirbitzCore#parseUri parseUri}
     * then {@link ParsedUri#fetchPaymentRequest fetchPaymentRequest}
     * @param request the payment request object include the BIP70 details
     */
    public void addPaymentRequest(PaymentRequest request) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_SpendAddPaymentRequest(mSpend, request.coreRequest(), error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     * Adds a transfer of funds between Wallets in an account. The source
     * wallet is the wallet that created this Spend and once the transaction is
     * sent, the source wallet is tagged with the metaData from this Spend
     * object.
     * @param destWallet the wallet to transfer funds to
     * @param amount the amount to transfer to the destWallet
     * @param destMeta the metadata for the transaction created for the destWallet
     */
    public void addTransfer(Wallet destWallet, long amount, MetadataSet destMeta) throws AirbitzException {
        SWIGTYPE_p_uint64_t ua = core.new_uint64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(ua), amount);

        String categoryText = "Transfer:Wallet:";
        if (meta().name() == null) {
            meta().name(destWallet.name());
        }
        if (meta().category() == null) {
            meta().category(categoryText + destWallet.name());
        }
        if (destMeta.name() == null) {
            destMeta.name(mWallet.name());
        }
        if (destMeta.category() == null) {
            destMeta.category(categoryText + mWallet.name());
        }

        tABC_Error error = new tABC_Error();
        core.ABC_SpendAddTransfer(mSpend, destWallet.id(),
            ua, destMeta.toTxDetails(), error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
        mIsTransfer = true;
    }

    public UnsentTransaction sign() throws AirbitzException {
        String rawTx = null;
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long tx = core.new_longp();
        SWIGTYPE_p_p_char pRawTx = core.longp_to_ppChar(tx);

        core.ABC_SpendSignTx(mSpend, pRawTx, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        } else {
            rawTx = Jni.getStringAtPtr(core.longp_value(tx));
        }
        return new UnsentTransaction(mAccount, mWallet, rawTx, this);
    }

    /**
     * Signs this send request and broadcasts it to the blockchain and saves it to the local database.
     * @return a transaction object for the new transaction
     */
    public Transaction signBroadcastSave() throws AirbitzException {
        UnsentTransaction utx = sign();
        Transaction tx = null;
        if (null != utx.base16Tx()) {
            utx.broadcast();
            tx = utx.save();
        }
        return tx;
    }

    /**
     * Get the maximum amount spendable from this wallet using the currenct Spend object
     * @return maximum spendable from this wallet in satoshis
     */
    public long maxSpendable() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_uint64_t result = core.new_uint64_tp();
        core.ABC_SpendGetMax(mSpend, result, error);
        long actual = Jni.get64BitLongAtPtr(Jni.getCPtr(result));
        return actual;
    }

    /**
     * Calculate the amount of fees needed to send this transaction
     * @return the amount of fees needed in satoshis
     */
    public long calcSendFees() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_uint64_t total = core.new_uint64_tp();
        core.ABC_SpendGetFee(mSpend, total, error);
        long fees = Jni.get64BitLongAtPtr(Jni.getCPtr(total));
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
        return fees;
    }

    /**
     * Change the fee level. Higher fees will result in faster confirmation times.
     * @param level the fee level, high, standard or low
     */
    public void feeLevel(FeeLevel level, long customFee) {
        SWIGTYPE_p_uint64_t ua = core.new_uint64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(ua), customFee);

        tABC_Error error = new tABC_Error();
        mFeeLevel = level;
        core.ABC_SpendSetFee(mSpend, level.value, ua, error);
    }

    private void updateMeta() {
        try {
            tABC_Error error = new tABC_Error();
            core.ABC_SpendSetMetadata(mSpend, mMeta.toTxDetails(), error);
            if (error.getCode() != tABC_CC.ABC_CC_Ok) {
                throw new AirbitzException(error.getCode(), error);
            }
        } catch (AirbitzException e) {
            AirbitzCore.loge(e.getMessage());
        }
    }
}
