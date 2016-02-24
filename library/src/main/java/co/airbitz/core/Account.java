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

import android.graphics.Bitmap;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_bool;
import co.airbitz.internal.SWIGTYPE_p_double;
import co.airbitz.internal.SWIGTYPE_p_int64_t;
import co.airbitz.internal.SWIGTYPE_p_int;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_unsigned_char;
import co.airbitz.internal.SWIGTYPE_p_unsigned_int;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_AsyncBitCoinInfo;
import co.airbitz.internal.tABC_AsyncEventType;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

public class Account {
    private static String TAG = Account.class.getSimpleName();

    public static int OTP_RESET_DELAY_SECS = 60 * 60 * 24 * 7;

    private String mUsername;
    private String mPassword;
    private AirbitzCore mApi;
    private Categories mCategories;
    Engine mEngine;
    AccountSettings mSettings;

    private boolean mOtpEnabled = false;

    public interface Callbacks {
        public void userRemotePasswordChange();
        public void userLoggedOut();
        public void userAccountChanged();
        public void userWalletsLoading();
        public void userWalletStatusChange(int loaded, int total);
        public void userWalletsLoaded();
        public void userWalletsChanged();
        public void userOTPRequired(String secret);
        public void userOtpResetPending();
        public void userExchangeRateChanged();
        public void userBlockHeightChanged();
        public void userBalanceUpdate();
        public void userIncomingBitcoin(Wallet wallet, Transaction transaction);
        public void userSweep(Wallet wallet, Transaction transaction, long amountSwept);

        public void userBitcoinLoading();
        public void userBitcoinLoaded();
    }
    Callbacks mCallbacks;

    Account(AirbitzCore api, String username, String password) {
        mApi = api;
        mUsername = username;
        mPassword = password;
        mCategories = new Categories(this);
        mEngine = new Engine(api, this);
        settings();
    }

    Engine engine() {
        return mEngine;
    }

    public void callbacks(Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    public String username() {
        return mUsername;
    }

    protected String password() {
        return mPassword;
    }

    public boolean wasPasswordLogin() {
        return mPassword != null;
    }

    public boolean isLoggedIn() {
        return mUsername != null;
    }

    public Categories categories() {
        return mCategories;
    }

    public DataStore data(String storeId) {
        return new DataStore(this, storeId);
    }

    public AccountSettings settings() {
        if (mSettings != null) {
            return mSettings;
        }
        try {
            mSettings = new AccountSettings(this).load();
            return mSettings;
        } catch (AirbitzException e) {
            AirbitzCore.debugLevel(1, "settings error:");
            return null;
        }
    }

    public boolean checkPassword(String password) {
        boolean check = false;
        if (password == null || password.isEmpty()) {
            check = !passwordExists();
        } else {
            tABC_Error error = new tABC_Error();
            SWIGTYPE_p_long lp = core.new_longp();
            SWIGTYPE_p_bool okay = Jni.newBool(Jni.getCPtr(lp));

            core.ABC_PasswordOk(mUsername, password, okay, error);
            if (error.getCode() == tABC_CC.ABC_CC_Ok) {
                check = Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
            } else {
                AirbitzCore.debugLevel(1, "Password OK error:"+ error.getSzDescription());
            }
        }
        return check;
    }

    public boolean passwordExists() {
        tABC_Error pError = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool exists = Jni.newBool(Jni.getCPtr(lp));

        tABC_CC result = core.ABC_PasswordExists(mUsername, exists, pError);
        if(pError.getCode().equals(tABC_CC.ABC_CC_Ok)) {
            return Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        } else {
            AirbitzCore.debugLevel(1, "Password Exists error:"+pError.getSzDescription());
            return true;
        }
    }

    public void pinSetup() throws AirbitzException {
        AccountSettings settings = settings();
        if (settings != null) {
            pinSetup(settings.settings().getSzPIN());
        }
    }

    public void pinSetup(String pin) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_PinSetup(mUsername, mPassword, pin, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(null, error.getCode(), error);
        }
    }

    public boolean checkPin(String pin) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_bool result = Jni.newBool(Jni.getCPtr(lp));
        core.ABC_PinCheck(mUsername, mPassword, pin, result, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            return Jni.getBytesAtPtr(Jni.getCPtr(lp), 1)[0] != 0;
        }
        return false;
    }

    public void logout() {
        mEngine.stop();
        mSettings = null;
        mCachedWallets = null;

        mApi.destroy();
    }

    public List<String> getWalletIds() {
        tABC_Error Error = new tABC_Error();
        List<String> uuids = new ArrayList<String>();

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int pUCount = core.int_to_uint(pCount);

        SWIGTYPE_p_long aUUIDS = core.new_longp();
        SWIGTYPE_p_p_p_char pppUUIDs = core.longp_to_pppChar(aUUIDS);

        tABC_CC result = core.ABC_GetWalletUUIDs(mUsername, mPassword,
                pppUUIDs, pUCount, Error);
        if (tABC_CC.ABC_CC_Ok == result)
        {
            if (core.longp_value(aUUIDS)!=0)
            {
                int count = core.intp_value(pCount);
                long base = core.longp_value(aUUIDS);
                for (int i = 0; i < count; i++)
                {
                    Jni.pLong temp = new Jni.pLong(base + i * 4);
                    long start = core.longp_value(temp);
                    if(start!=0) {
                        uuids.add(Jni.getStringAtPtr(start));
                    }
                }
            }
        }
        return uuids;
    }

    List<Wallet> mCachedWallets = null;
    public List<Wallet> getWallets() {
        return mCachedWallets;
    }

    public List<Wallet> getActiveWallets() {
        List<Wallet> wallets = getWallets();
        if (wallets == null) {
            return null;
        }
        List<Wallet> out = new ArrayList<Wallet>();
        for (Wallet w: wallets) {
            if (!w.isArchived()) {
                out.add(w);
            }
        }
        return out;
    }

    public boolean createWallet(String walletName, String currency) {
        AirbitzCore.debugLevel(1, "createWallet(" + walletName + "," + currency + ")");
        tABC_Error pError = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        int currencyNum = Currencies.instance().map(currency);
        tABC_CC result = core.ABC_CreateWallet(
                mUsername, mPassword,
                walletName, currencyNum, ppChar, pError);
        if (result == tABC_CC.ABC_CC_Ok) {
            mEngine.startWatchers();
            return true;
        } else {
            AirbitzCore.debugLevel(1, "Create wallet failed - "+pError.getSzDescription()+", at "+pError.getSzSourceFunc());
            return result == tABC_CC.ABC_CC_Ok;
        }
    }

    public void passwordSetup(String password) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        tABC_CC cc = core.ABC_ChangePassword(
            mUsername, password, password, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
        mPassword = password;
    }

    public void recoverySetup(String recoveryAnswers) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        tABC_CC cc = core.ABC_ChangePasswordWithRecoveryAnswers(
                        mUsername, recoveryAnswers, mPassword, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
    }

    public void saveRecoveryAnswers(String questions, String answers) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_SetAccountRecoveryQuestions(mUsername,
                mPassword, questions, answers, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
    }

    public Wallet getWallet(String uuid) {
        if (uuid == null) {
            return null;
        }
        List<Wallet> wallets = getWallets();
        if (wallets == null) {
            return null;
        }
        for (Wallet w : wallets) {
            if (uuid.equals(w.id())) {
                return w;
            }
        }
        return null;
    }

    public void walletReorder(List<Wallet> wallets) {
        boolean archived = false; // non-archive
        StringBuffer uuids = new StringBuffer("");
        for (Wallet wallet : wallets) {
            uuids.append(wallet.id()).append("\n");
        }

        tABC_Error error = new tABC_Error();
        tABC_CC result = core.ABC_SetWalletOrder(
            mUsername, mPassword,
            uuids.toString().trim(), error);
        if (result != tABC_CC.ABC_CC_Ok) {
            AirbitzCore.debugLevel(1, "Error: CoreBridge.setWalletOrder" + error.getSzDescription());
        }
    }

    void sendReloadWallets() {
        mEngine.sendReloadWallets();
    }

    public void reloadWallets() {
        mEngine.reloadWallets();
    }

    public void startAllAsyncUpdates() {
        mEngine.start();
    }

    public void waitOnWatchers() {
        mEngine.waitOnWatchers();
    }

    public void deleteWatcherCache() {
        mEngine.deleteWatcherCache();
    }

    public void stopAllAsyncUpdates() {
        mEngine.stop();
    }

    public static class BitidSignature {
        public String address;
        public String signature;
    }

    public String parseBitidUri(String uri) {
        tABC_Error error = new tABC_Error();
        String urlDomain = null;

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        core.ABC_BitidParseUri(mUsername, null, uri, ppChar, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            urlDomain = Jni.getStringAtPtr(core.longp_value(lp));
        }
        return urlDomain;
    }

    public BitidSignature bitidSignature(String uri, String message) {
        BitidSignature bitid = new BitidSignature();

        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long pAddress = core.new_longp();
        SWIGTYPE_p_p_char ppAddress = core.longp_to_ppChar(pAddress);
        SWIGTYPE_p_long pSignature = core.new_longp();
        SWIGTYPE_p_p_char ppSignature = core.longp_to_ppChar(pSignature);

        tABC_CC result = core.ABC_BitidSign(
            mUsername, mPassword, uri, message, ppAddress, ppSignature, error);
        if (result == tABC_CC.ABC_CC_Ok) {
            bitid.address = Jni.getStringAtPtr(core.longp_value(pAddress));
            bitid.signature = Jni.getStringAtPtr(core.longp_value(pSignature));
        }
        return bitid;
    }

    public boolean bitidLogin(String uri) {
        tABC_Error error = new tABC_Error();
        core.ABC_BitidLogin(mUsername, null, uri, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    void restoreConnectivity() {
        mEngine.restoreConnectivity();
    }

    void lostConnectivity() {
        mEngine.lostConnectivity();
    }

    public void updateExchangeRates() {
        mEngine.updateExchangeRates();
    }

    private void requestExchangeRateUpdate(final String currency) {
        mEngine.requestExchangeRateUpdate(currency);
    }

    public String formatDefaultCurrency(double in) {
        AccountSettings settings = settings();
        if (settings != null) {
            String pre = settings.bitcoinDenomination().btcSymbol();
            String out = String.format("%.3f", in);
            return pre+out;
        }
        return "";
    }

    public String formatCurrency(double in, String currency, boolean withSymbol) {
        return formatCurrency(in, currency, withSymbol, 2);
    }

    public String formatCurrency(double in, String currency, boolean withSymbol, int decimalPlaces) {
        String pre;
        String denom = Currencies.instance().currencySymbol(currency) + " ";
        if (in < 0)
        {
            in = Math.abs(in);
            pre = withSymbol ? "-" + denom : "-";
        } else {
            pre = withSymbol ? denom : "";
        }
        BigDecimal bd = new BigDecimal(in);
        DecimalFormat df;
        switch(decimalPlaces) {
            case 3:
                df = new DecimalFormat("#,##0.000", new DecimalFormatSymbols(Locale.getDefault()));
                break;
            default:
                df = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.getDefault()));
                break;
        }

        return pre + df.format(bd.doubleValue());
    }

    private int userDecimalPlaces() {
        int decimalPlaces = 8; // for BitcoinDenomination.BTC
        AccountSettings settings = settings();
        if (settings == null) {
            return 2;
        }
        BitcoinDenomination bitcoinDenomination =
            settings.bitcoinDenomination();
        if (bitcoinDenomination != null) {
            int label = bitcoinDenomination.getDenominationType();
            if (label == BitcoinDenomination.UBTC)
                decimalPlaces = 2;
            else if (label == BitcoinDenomination.MBTC)
                decimalPlaces = 5;
        }
        return decimalPlaces;
    }

    public String formatSatoshi(long amount) {
        return formatSatoshi(amount, true);
    }

    public String formatSatoshi(long amount, boolean withSymbol) {
        return formatSatoshi(amount, withSymbol, userDecimalPlaces());
    }

    public String formatSatoshi(long amount, boolean withSymbol, int decimals) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        int decimalPlaces = userDecimalPlaces();

        boolean negative = amount < 0;
        if(negative)
            amount = -amount;
        int result = Jni.FormatAmount(amount, Jni.getCPtr(ppChar), decimalPlaces, false, Jni.getCPtr(error));
        if ( result != 0)
        {
            return "";
        }
        else {
            decimalPlaces = decimals > -1 ? decimals : decimalPlaces;
            String pretext = "";
            if (negative) {
                pretext += "-";
            }
            if (withSymbol) {
                pretext += userBtcSymbol();
            }

            BigDecimal bd = new BigDecimal(amount);
            bd = bd.movePointLeft(decimalPlaces);

            DecimalFormat df = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.getDefault()));

            if(decimalPlaces == 5) {
                df = new DecimalFormat("#,##0.#####", new DecimalFormatSymbols(Locale.getDefault()));
            }
            else if(decimalPlaces == 8) {
                df = new DecimalFormat("#,##0.########", new DecimalFormatSymbols(Locale.getDefault()));
            }

            return pretext + df.format(bd.doubleValue());
        }
    }

    public long denominationToSatoshi(String amount) {
        int decimalPlaces = userDecimalPlaces();

        try {
            // Parse using the current locale
            Number cleanAmount =
                new DecimalFormat().parse(amount, new ParsePosition(0));
            if (null == cleanAmount) {
                return 0L;
            }
            // Convert to BD so we don't lose precision
            BigDecimal bd = BigDecimal.valueOf(cleanAmount.doubleValue());
            DecimalFormat df = new DecimalFormat("###0.############", new DecimalFormatSymbols(Locale.getDefault()));
            String bdstr = df.format(bd.doubleValue());
            long parseamt = Jni.ParseAmount(bdstr, decimalPlaces);
            long max = Math.max(parseamt, 0);
            return max;
        } catch (Exception e) {
            // Shhhhh
        }
        return 0L;
    }

    public String formatCurrency(long satoshi, String currency, boolean btc, boolean withSymbol) {
        String out;
        if (!btc) {
            double o = satoshiToCurrency(satoshi, currency);
            out = formatCurrency(o, currency, withSymbol);
        } else {
            out = formatSatoshi(satoshi, withSymbol, 2);
        }
        return out;
    }

    public double satoshiToCurrency(long satoshi, String currency) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_double amountFiat = core.new_doublep();

        int currencyNum = Currencies.instance().map(currency);
        long out = Jni.satoshiToCurrency(mUsername, mPassword,
                satoshi, Jni.getCPtr(amountFiat), currencyNum, Jni.getCPtr(error));
        return core.doublep_value(amountFiat);
    }

    public long parseFiatToSatoshi(String amount, String currency) {
        try {
             Number cleanAmount =
                new DecimalFormat().parse(amount, new ParsePosition(0));
             if (null == cleanAmount) {
                 return 0;
             }
            double amountFiat = cleanAmount.doubleValue();
            long satoshi = currencyToSatoshi(amountFiat, currency);

            // Round up to nearest 1 bits, .001 mBTC, .00001 BTC
            satoshi = 100 * (satoshi / 100);
            return satoshi;

        } catch (NumberFormatException e) {
            /* Sshhhhh */
        }
        return 0;
    }

    public long currencyToSatoshi(double amount, String currency) {
        tABC_Error error = new tABC_Error();
        tABC_CC result;
        SWIGTYPE_p_int64_t satoshi = core.new_int64_tp();
        SWIGTYPE_p_long l = core.p64_t_to_long_ptr(satoshi);

        int currencyNum = Currencies.instance().map(currency);
        result = core.ABC_CurrencyToSatoshi(mUsername, mPassword,
            amount, currencyNum, satoshi, error);

        return Jni.get64BitLongAtPtr(Jni.getCPtr(l));
    }

    public void otpAuthGet() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long ptimeout = core.new_longp();
        SWIGTYPE_p_int lp = core.new_intp();
        SWIGTYPE_p_bool pbool = Jni.newBool(Jni.getCPtr(lp));

        core.ABC_OtpAuthGet(
            mUsername, mPassword,
            pbool, ptimeout, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }

        mOtpEnabled = core.intp_value(lp)==1;
    }

    public void otpSetup() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpAuthSet(mUsername, mPassword, OTP_RESET_DELAY_SECS, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            mOtpEnabled = true;
        } else {
            throw new AirbitzException(null, error.getCode(), error);
        }
    }

    public void otpDisable() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpAuthRemove(mUsername, mPassword, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            mOtpEnabled = false;
            core.ABC_OtpKeyRemove(mUsername, error);
        } else {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
    }

    public void otpResetCancel() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_OtpResetRemove(mUsername, mPassword, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(mApi.getContext(), error.getCode(), error);
        }
    }

    public boolean isOtpEnabled() {
        return mOtpEnabled;
    }

    public String otpSecret() {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);
        tABC_CC cc = core.ABC_OtpKeyGet(mUsername, ppChar, error);
        String secret = cc == tABC_CC.ABC_CC_Ok ? Jni.getStringAtPtr(core.longp_value(lp)) : null;
        return secret;
    }

    boolean mOTPError = false;
    public boolean hasOTPError() {
        return mOTPError;
    }

    public void otpSetError(tABC_CC cc) {
        mOTPError = tABC_CC.ABC_CC_InvalidOTP == cc;
    }

    public void otpSetError(AirbitzException error) {
        mOTPError = error.isOtpError();
    }

    public void otpClearError() {
        mOTPError = false;
    }

    public byte[] otpQrCode() {
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_unsigned_char ppData = core.longp_to_unsigned_ppChar(lp);

        SWIGTYPE_p_int pWidth = core.new_intp();
        SWIGTYPE_p_unsigned_int pWCount = core.int_to_uint(pWidth);

        tABC_Error error = new tABC_Error();
        tABC_CC cc = core.ABC_QrEncode(otpSecret(), ppData, pWCount, error);
        if (cc == tABC_CC.ABC_CC_Ok) {
            int width = core.intp_value(pWidth);
            return Jni.getBytesAtPtr(core.longp_value(lp), width*width);
        } else {
            return null;
        }
    }

    public Bitmap otpQrCodeBitmap() {
        byte[] array = otpQrCode();
        if (null != array) {
            return mApi.qrEncode(array, (int) Math.sqrt(array.length), 4);
        } else {
            return null;
        }
    }

    private String userBtcSymbol() {
        AccountSettings settings = settings();
        if (settings == null) {
            return "";
        }
        BitcoinDenomination bitcoinDenomination =
            settings.bitcoinDenomination();
        if (bitcoinDenomination == null) {
            AirbitzCore.debugLevel(1, "Bad bitcoin denomination from core settings");
            return "";
        }
        return bitcoinDenomination.btcSymbol();
    }
}
