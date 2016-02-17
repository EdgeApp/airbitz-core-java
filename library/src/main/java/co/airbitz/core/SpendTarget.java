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

import android.text.TextUtils;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_SpendTarget;
import co.airbitz.internal.SWIGTYPE_p_uint64_t;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;
import co.airbitz.internal.tABC_SpendTarget;

public class SpendTarget {
    SWIGTYPE_p_long _lpSpend;
    SWIGTYPE_p_p_sABC_SpendTarget _pSpendSWIG;
    tABC_SpendTarget _pSpend;
    private tABC_Error pError;
    private long bizId;
    private double mAmountFiat;

    private Account mAccount;
    private Wallet mWallet;

    SpendTarget(Account account, Wallet wallet) {
        _lpSpend = core.new_longp();
        _pSpendSWIG = core.longPtr_to_ppSpendTarget(_lpSpend);
        _pSpend = null;
        pError = new tABC_Error();
        mAccount = account;
        mWallet = wallet;
    }

    public void dealloc() {
        if (_pSpend != null) {
            _pSpend = null;
            pError = null;
        }
    }

    public tABC_SpendTarget getSpend() {
        return _pSpend;
    }

    public long getSpendAmount() {
        return Jni.get64BitLongAtPtr(Jni.getCPtr(_pSpend.getAmount()));
    }

    public boolean isTransfer() {
        return !TextUtils.isEmpty(_pSpend.getSzDestUUID());
    }

    public void setSpendAmount(long amount) {
        SWIGTYPE_p_uint64_t ua = core.new_uint64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(ua), amount);
        _pSpend.setAmount(ua);
    }

    public boolean newSpend(String text) {
        tABC_Error pError = new tABC_Error();
        core.ABC_SpendNewDecode(text, _pSpendSWIG, pError);
        _pSpend = new Spend(core.longp_value(_lpSpend));
        return pError.getCode() == tABC_CC.ABC_CC_Ok;
    }

    public boolean newTransfer(String walletUUID) {
        SWIGTYPE_p_uint64_t amount = core.new_uint64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(amount), 0);
        core.ABC_SpendNewTransfer(
                mAccount.getUsername(), walletUUID,
                amount, _pSpendSWIG, pError);
        _pSpend = new Spend(core.longp_value(_lpSpend));
        return pError.getCode() == tABC_CC.ABC_CC_Ok;
    }

    public boolean spendNewInternal(String address, String label, String category,
                                    String notes, long amountSatoshi) {
        SWIGTYPE_p_uint64_t amountS = core.new_uint64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(amountS), amountSatoshi);

        core.ABC_SpendNewInternal(address, label,
                category, notes, amountS, _pSpendSWIG, pError);
        _pSpend = new Spend(core.longp_value(_lpSpend));
        return pError.getCode() == tABC_CC.ABC_CC_Ok;
    }

    public void setBizId(long bizId) {
        this.bizId = bizId;
    }

    public long getBizId() {
        return this.bizId;
    }

    public void setAmountFiat(double amountFiat) {
        this.mAmountFiat = amountFiat;
    }

    public double getAmountFiat() {
        return mAmountFiat;
    }

    public UnsentTransaction sign() throws AirbitzException {
        String rawTx = null;
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long tx = core.new_longp();
        SWIGTYPE_p_p_char pRawTx = core.longp_to_ppChar(tx);

        core.ABC_SpendSignTx(
                mAccount.getUsername(), mWallet.getUUID(),
                _pSpend, pRawTx, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            rawTx = Jni.getStringAtPtr(core.longp_value(tx));
        } else {
            throw new AirbitzException(null, error.getCode(), error);
        }
        return new UnsentTransaction(mAccount, mWallet, rawTx, this);
    }

    public Transaction signBroadcastSave() throws AirbitzException {
        UnsentTransaction utx = sign();
        Transaction tx = null;
        if (null != utx.base16Tx() && utx.broadcast()) {
            tx = utx.save();
        }
        return tx;
    }

    public long maxSpendable() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_uint64_t result = core.new_uint64_tp();

        core.ABC_SpendGetMax(
                mAccount.getUsername(), mWallet.getUUID(),
                _pSpend, result, pError);
        long actual = Jni.get64BitLongAtPtr(Jni.getCPtr(result));
        return actual;
    }

    public long calcSendFees() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_uint64_t total = core.new_uint64_tp();
        core.ABC_SpendGetFee(
                mAccount.getUsername(), mWallet.getUUID(),
                _pSpend, total, error);

        long fees = Jni.get64BitLongAtPtr(Jni.getCPtr(total));
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(null, error.getCode(), error);
        }
        return fees;
    }

    public class Spend extends tABC_SpendTarget {
        public Spend(long pv) {
            super(pv, false);
        }
        public long getPtr(tABC_SpendTarget p) {
            return getCPtr(p);
        }
    }
}
