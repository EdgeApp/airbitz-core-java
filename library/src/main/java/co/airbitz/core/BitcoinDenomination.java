package co.airbitz.core;

import co.airbitz.internal.SWIGTYPE_p_int64_t;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_BitcoinDenomination;

public class BitcoinDenomination {

    tABC_BitcoinDenomination mDenomination;

    public static final int BTC = 0;
    public static final int MBTC = 1;
    public static final int UBTC = 2;

    protected BitcoinDenomination(tABC_BitcoinDenomination denomination) {
        mDenomination = denomination;
    }

    protected tABC_BitcoinDenomination get() {
        return mDenomination;
    }

    public void setDenominationType(int value) {
        mDenomination.setDenominationType(value);
        if (MBTC == value) {
            SWIGTYPE_p_int64_t amt = core.new_int64_tp();
            core.longp_assign(core.p64_t_to_long_ptr(amt), 100000);
            mDenomination.setSatoshi(amt);
        } else if (UBTC == value) {
            SWIGTYPE_p_int64_t amt = core.new_int64_tp();
            core.longp_assign(core.p64_t_to_long_ptr(amt), 100);
            mDenomination.setSatoshi(amt);
        } else if (BTC == value) {
            SWIGTYPE_p_int64_t amt = core.new_int64_tp();
            core.longp_assign(core.p64_t_to_long_ptr(amt), 100000000);
            mDenomination.setSatoshi(amt);
        }
    }

    public int getDenominationType() {
        return mDenomination.getDenominationType();
    }

    public String btcSymbol() {
        return mBtcSymbols[getDenominationType()];
    }

    public String btcLabel() {
        return mBtcDenominations[getDenominationType()];
    }

    private String[] mBtcSymbols = {"Ƀ ", "mɃ ", "ƀ "};
    private String[] mBtcDenominations = {"BTC", "mBTC", "bits"};
}
