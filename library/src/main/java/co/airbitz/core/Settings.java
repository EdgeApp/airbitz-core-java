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

/**
 * Settings represent settings for the associated {@link Account}. Like all
 * other account info, these settings are locally encrypted and synchronized
 * between devices.
 */
public class Settings {
    private static String TAG = AirbitzCore.class.getSimpleName();

    private Account mAccount;
    private tABC_AccountSettings mSettings;

    protected Settings(Account account) {
        mAccount = account;
    }

    /**
     * Load the account settings from disk.
     * @return this with the settings from disk
     */
    public Settings load() throws AirbitzException {
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
            throw new AirbitzException(error.getCode(), error);
        }
        return this;
    }

    tABC_AccountSettings settings() {
        return mSettings;
    }

    void setupDefaultCurrency() {
        currency(Currencies.instance().defaultCurrency().code);
        try {
            save();
        } catch (AirbitzException e) {
            AirbitzCore.loge("setupDefaultCurrency error:");
        }
    }

    /**
     * Save the settings, persisting them to disk.
     */
    public void save() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        core.ABC_UpdateAccountSettings(mAccount.username(), mAccount.password(), mSettings, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
    }

    /**
     * Update the user first name.
     * @param value the first name of the user
     */
    public void firstName(String value) {
        settings().setSzFirstName(value);
    }

    private boolean empty(String s) {
        return s == null || s.length() == 0;
    }

    /**
     * Retrieve the display name of the user. Currently this is `firstName
     * lastName - nickName`.
     * @return the display name for the user
     */
    public String displayName() {
        if (settings().getBNameOnPayments()) {
            StringBuffer buf = new StringBuffer("");
            if (!empty(firstName())) {
                buf.append(firstName());
            }
            if (buf.length() != 0) {
                buf.append(" ");
            }
            if (!empty(lastName())) {
                buf.append(lastName());
            }
            if (!empty(nickName())) {
                if (buf.length() != 0) {
                    buf.append(" - ");
                }
                buf.append(nickName());
            }
            return buf.toString().trim();
        }
        return "";
    }

    /**
     * Retrieve the first name of the user.
     * @return the first name for the user
     */
    public String firstName() {
        return settings().getSzFirstName();
    }

    /**
     * Update the user last name.
     * @param value the last name of the user
     */
    public void lastName(String value) {
        settings().setSzLastName(value);
    }

    /**
     * Retrieve the last name of the user.
     * @return the last name for the user
     */
    public String lastName() {
        return settings().getSzLastName();
    }

    /**
     * Update the nick name.
     * @param value the nickname of the user
     */
    public void nickName(String value) {
        settings().setSzNickname(value);
    }

    /**
     * Retrieve the nick name of the user.
     * @return the nick name for the user
     */
    public String nickName() {
        return settings().getSzNickname();
    }

    /**
     * Set the PIN of the user
     */
    public void pin(String value) {
        settings().setSzPIN(value);
    }

    /**
     * Retrieve the pin of the user.
     * @return the pin for the user
     */
    public String pin() {
        return settings().getSzPIN();
    }

    /**
     * Set whether the user wants to include their display name on {@link
     * ReceiveAddress} URIs. This is not enforced inside ABC. The application
     * developer must include the display name if the user desires.
     * @param value true indicates to include the display on payment URIs
     */
    public void showNameOnPayments(boolean value) {
        settings().setBNameOnPayments(value);
    }

    /**
     * Retrieve if the user wants their display name to be included in payment URIs.
     * @return true if the user wants their display included
     */
    public boolean nameOnPayments() {
        return settings().getBNameOnPayments();
    }

    /**
     * Set the preferred number of seconds for a user before auto-logged out.
     * @param value seconds
     */
    public void secondsAutoLogout(int value) {
        settings().setSecondsAutoLogout(value);
    }

    /**
     * Retrieve the preferred number of seconds for a user before auto-logged out.
     * @return the number of seconds of inactivity before being auto-logged out.
     */
    public int secondsAutoLogout() {
        return settings().getSecondsAutoLogout();
    }

    /**
     * Set the number of times the user has been reminder to setup password recovery.
     * @param value the number of reminders displayed to the user
     */
    public void recoveryReminderCount(int value) {
        settings().setRecoveryReminderCount(value);
    }

    /**
     * Retrieve the number of times the user has been reminded to setup password recovery.
     * @return the number of reminders displayed to the user
     */
    public int recoveryReminderCount() {
        return settings().getRecoveryReminderCount();
    }

    /**
     * Set the preferred language for this user.
     * @param value the preferred language for this user
     */
    public void language(String value) {
        settings().setSzLanguage(value);
    }

    /**
     * Return the preferred language for this user
     * @return the preferred language for this user
     */
    public String language() {
        return settings().getSzLanguage();
    }

    /**
     * Set the preferred currency for this user
     * @param currencyCode the 3 letter ISO code for the preferred currency
     */
    public void currency(String currencyCode) {
        settings().setCurrencyNum(
                Currencies.instance().map(currencyCode));
    }

    /**
     * Retrieve the preferred currency for this user
     * @return the preferred currency represented as a {@link CoreCurrency}
     */
    public CoreCurrency currency() {
        if (settings() != null) {
            return Currencies.instance().lookup(
                Currencies.instance().map(settings().getCurrencyNum()));
        } else {
            return Currencies.instance().defaultCurrency();
        }
    }

    /**
     * Set the preferred exchange rate source. Choices can be found in {@link
     * AirbitzCore#exchangeRateSources exchangeRateSources}.
     * @param value the name of the exchange rate source
     */
    public void exchangeRateSource(String value) {
        settings().setSzExchangeRateSource(value);
    }

    /**
     * Retrieve the preferred exchange rate source for this user
     * @return the preferred exchange rate source
     */
    public String exchangeRateSource() {
        return settings().getSzExchangeRateSource();
    }

    /**
     * Set the preferred bitcoin denomination for this user.
     * @param value the preferred bitcoin denomination
     */
    public void bitcoinDenomination(BitcoinDenomination value) {
        settings().setBitcoinDenomination(value.get());
    }

    /**
     * Get the preferred bitcoin denomination for this user.
     * @return the preferred bitcoin denomination
     */
    public BitcoinDenomination bitcoinDenomination() {
        return new BitcoinDenomination(settings().getBitcoinDenomination());
    }

    public void fullName(String value) {
        settings().setSzFullName(value);
    }

    public String fullName() {
        return settings().getSzFullName();
    }

    /**
     * Set if the user prefers to enforce a spend limit.
     * @param value true if the user wants a spend limit, false otherwise
     */
    public void dailySpendLimit(boolean value) {
        settings().setBDailySpendLimit(value);
    }

    /**
     * Retrieve if the daily spend limit should be enforced.
     * @return true if the daily spend limit in should be enforced, false
     * otherwise.
     */
    public boolean dailySpendLimit() {
        return settings().getBDailySpendLimit();
    }

    /**
     * Set the daily spend limit. After the limit is exceed, the {@link
     * Account#checkPassword checkPassword} should be use to authenticate the user before a
     * spend.
     * @param spendLimit the daily spend limit in satoshis
     */
    public void dailySpendLimitSatoshis(long spendLimit) {
        SWIGTYPE_p_int64_t limit = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(limit), spendLimit);
        settings().setDailySpendLimitSatoshis(limit);
    }

    /**
     * Retrieve if the daily spend limit.
     * @return the daily spend limit in satoshis
     */
    public long dailySpendLimitSatoshis() {
        SWIGTYPE_p_int64_t satoshi = settings().getDailySpendLimitSatoshis();
        return Jni.get64BitLongAtPtr(Jni.getCPtr(satoshi));
    }

    /**
     * Set if the user prefers to require their PIN to be entered before spending.
     * @param value true if the user wants PIN check prior to spends.
     */
    public void spendRequirePin(boolean value) {
        settings().setBSpendRequirePin(value);
    }

    /**
	 * Retrieve if the PIN should be required to spend.
	 * @return true if the PIN should be required to spend, false otherwise.
     */
    public boolean spendRequirePin() {
        return settings().getBSpendRequirePin();
    }

    /**
     * Set the amount to require PIN check before a spend. After the limit is exceed, the {@link
     * Account#checkPin checkPin} should be use to authenticate the user before a
     * spend.
     * @param spendLimit the spend amount to require PIN authentication for.
     */
    public void spendRequirePinSatoshis(long spendLimit) {
        SWIGTYPE_p_int64_t limit = core.new_int64_tp();
        Jni.set64BitLongAtPtr(Jni.getCPtr(limit), spendLimit);
        settings().setSpendRequirePinSatoshis(limit);
    }

    /**
     * Retrieve if the daily spend limit.
     * @return the daily spend limit in satoshis
     */
    public long spendRequirePinSatoshis() {
        SWIGTYPE_p_int64_t satoshi = settings().getSpendRequirePinSatoshis();
        return Jni.get64BitLongAtPtr(Jni.getCPtr(satoshi));
    }

    /**
     * Set the PIN login count for this user. After a PIN login this number should be incremented.
     * @param value the number of PIN logins for this user
     */
    public void pinLoginCount(int value) {
        settings().setPinLoginCount(value);
    }

    /**
     * Retrieve the PIN login count for this user
     * @return value the number of PIN logins for this user
     */
    public int pinLoginCount() {
        return settings().getPinLoginCount();
    }

    /**
     * Set the whether the user can login with their fingerprint. This is currently only supported on iOS.
     * @param value true if the fingerprint login should be disabled, false otherwise.
     */
    public void disableFingerprintLogin(boolean value) {
        settings().setBDisableFingerprintLogin(value);
    }

    /**
     * Retrieve if the fingerprint login has been disabled.
     * @return true if the fingerprint login has been disabled.
     */
    public boolean disableFingerprintLogin() {
        return settings().getBDisableFingerprintLogin();
    }
}
