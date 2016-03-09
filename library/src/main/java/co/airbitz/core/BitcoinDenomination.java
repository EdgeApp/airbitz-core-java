package co.airbitz.core;

import co.airbitz.internal.SWIGTYPE_p_int64_t;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_BitcoinDenomination;

import java.util.ArrayList;
import java.util.List;

public class BitcoinDenomination {

    tABC_BitcoinDenomination mDenomination;

    private int mMultiplier;

    public static final int BTC = 0;
    public static final int MBTC = 1;
    public static final int UBTC = 2;

    static List<BitcoinDenomination> denominations() {
        List<BitcoinDenomination> list = new ArrayList<BitcoinDenomination>();
        list.add(new BitcoinDenomination(BTC));
        list.add(new BitcoinDenomination(MBTC));
        list.add(new BitcoinDenomination(UBTC));
        return list;
    }

    BitcoinDenomination(int value) {
        this(new tABC_BitcoinDenomination());
        setDenominationType(value);
    }

    BitcoinDenomination(tABC_BitcoinDenomination denomination) {
        mDenomination = denomination;
    }

    protected tABC_BitcoinDenomination get() {
        return mDenomination;
    }

    public void setDenominationType(int value) {
        mDenomination.setDenominationType(value);
        if (MBTC == value) {
            mMultiplier = 100000;
            SWIGTYPE_p_int64_t amt = core.new_int64_tp();
            core.longp_assign(core.p64_t_to_long_ptr(amt), 100000);
            mDenomination.setSatoshi(amt);
        } else if (UBTC == value) {
            mMultiplier = 100;
            SWIGTYPE_p_int64_t amt = core.new_int64_tp();
            core.longp_assign(core.p64_t_to_long_ptr(amt), 100);
            mDenomination.setSatoshi(amt);
        } else if (BTC == value) {
            mMultiplier = 100000000;
            SWIGTYPE_p_int64_t amt = core.new_int64_tp();
            core.longp_assign(core.p64_t_to_long_ptr(amt), mMultiplier);
            mDenomination.setSatoshi(amt);
        }
    }

    public int type() {
        return mDenomination.getDenominationType();
    }

    public String btcSymbol() {
        return mBtcSymbols[type()];
    }

    public String btcLabel() {
        return mBtcDenominations[type()];
    }

    public int multiplier() {
        return mMultiplier;
    }

    private String[] mBtcSymbols = {"Ƀ ", "mɃ ", "ƀ "};
    private String[] mBtcDenominations = {"BTC", "mBTC", "bits"};
}
