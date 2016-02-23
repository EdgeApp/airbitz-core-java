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

import android.util.Log;

import java.util.Currency;
import java.util.Locale;
import java.util.Map;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_int64_t;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_AccountSettings;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_AccountSettings;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

public class AccountSettings {
    private static String TAG = AirbitzCore.class.getSimpleName();

    private Account mAccount;
    private tABC_AccountSettings mSettings;

    protected AccountSettings(Account account) {
        mAccount = account;
    }

    protected AccountSettings load() throws AirbitzException {
        tABC_Error error = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_sABC_AccountSettings pAccountSettings = core.longp_to_ppAccountSettings(lp);

        core.ABC_LoadAccountSettings(mAccount.username(), mAccount.password(), pAccountSettings, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            mSettings = Jni.newAccountSettings(core.longp_value(lp));
            if (mSettings.getCurrencyNum() == 0) {
                setupDefaultCurrency();
            }
        } else {
            throw new AirbitzException(null, error.getCode(), error);
        }
        return this;
    }

    public tABC_AccountSettings settings() {
        return mSettings;
    }

    protected void setupDefaultCurrency() {
        currencyCode(Currencies.instance().defaultCurrency().code);
        try {
            save();
        } catch (AirbitzException e) {
            AirbitzCore.debugLevel(1, "setupDefaultCurrency error:");
        }
    }

    public void save() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_UpdateAccountSettings(mAccount.username(), mAccount.password(), mSettings, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(null, error.getCode(), error);
        }
    }

    public void firstName(String value) {
        settings().setSzFirstName(value);
    }

    public String firstName() {
        return settings().getSzFirstName();
    }

    public void lastName(String value) {
        settings().setSzLastName(value);
    }

    public String lastName() {
        return settings().getSzLastName();
    }

    public void nickName(String value) {
        settings().setSzNickname(value);
    }

    public String nickname() {
        return settings().getSzNickname();
    }

    public void pin(String value) {
        settings().setSzPIN(value);
    }

    public String pin() {
        return settings().getSzPIN();
    }

    public void showNameOnPayments(boolean value) {
        settings().setBNameOnPayments(value);
    }

    public boolean nameOnPayments() {
        return settings().getBNameOnPayments();
    }

    public void secondsAutoLogout(int value) {
        settings().setSecondsAutoLogout(value);
    }

    public int secondsAutoLogout() {
        return settings().getSecondsAutoLogout();
    }

    public void recoveryReminderCount(int value) {
        settings().setRecoveryReminderCount(value);
    }

    public int recoveryReminderCount() {
        return settings().getRecoveryReminderCount();
    }

    public void language(String value) {
        settings().setSzLanguage(value);
    }

    public String language() {
        return settings().getSzLanguage();
    }

    public void currencyCode(String value) {
        settings().setCurrencyNum(
                Currencies.instance().map(value));
    }

    public String currencyCode() {
        return Currencies.instance().map(settings().getCurrencyNum());
    }

    public void exchangeRateSource(String value) {
        settings().setSzExchangeRateSource(value);
    }

    public String exchangeRateSource() {
        return settings().getSzExchangeRateSource();
    }

    public void bitcoinDenomination(BitcoinDenomination value) {
        settings().setBitcoinDenomination(value.get());
    }

    public BitcoinDenomination bitcoinDenomination() {
        return new BitcoinDenomination(settings().getBitcoinDenomination());
    }

    public void advancedFeatures(boolean value) {
        settings().setBAdvancedFeatures(value);
    }

    public boolean advancedFeatures() {
        return settings().getBAdvancedFeatures();
    }

    public void fullName(String value) {
        settings().setSzFullName(value);
    }

    public String fullName() {
        return settings().getSzFullName();
    }

    public void dailySpendLimit(boolean value) {
        settings().setBDailySpendLimit(value);
    }

    public boolean dailySpendLimit() {
        return settings().getBDailySpendLimit();
    }

    public void dailySpendLimitSatoshis(long spendLimit) {
        SWIGTYPE_p_int64_t limit = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(limit), spendLimit);
        settings().setDailySpendLimitSatoshis(limit);
    }

    public long dailySpendLimitSatoshis() {
        SWIGTYPE_p_int64_t satoshi = settings().getDailySpendLimitSatoshis();
        return Jni.get64BitLongAtPtr(Jni.getCPtr(satoshi));
    }

    public void spendRequirePin(boolean value) {
        settings().setBSpendRequirePin(value);
    }

    public boolean spendRequirePin() {
        return settings().getBSpendRequirePin();
    }

    public void spendRequirePinSatoshis(long spendLimit) {
        SWIGTYPE_p_int64_t limit = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(limit), spendLimit);
        settings().setSpendRequirePinSatoshis(limit);
    }

    public long spendRequirePinSatoshis() {
        SWIGTYPE_p_int64_t satoshi = settings().getSpendRequirePinSatoshis();
        return Jni.get64BitLongAtPtr(Jni.getCPtr(satoshi));
    }

    public void disablePINLogin(boolean value) {
        settings().setBDisablePINLogin(value);
    }

    public boolean disablePINLogin() {
        return settings().getBDisablePINLogin();
    }

    public void pinLoginCount(int value) {
        settings().setPinLoginCount(value);
    }

    public int pinLoginCount() {
        return settings().getPinLoginCount();
    }

    public void disableFingerprintLogin(boolean value) {
        settings().setBDisableFingerprintLogin(value);
    }

    public boolean disableFingerprintLogin() {
        return settings().getBDisableFingerprintLogin();
    }
}
