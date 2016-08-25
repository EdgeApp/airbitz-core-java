package co.airbitz.core;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

/**
 * AirbitzException is the generic exception thrown when interacting with
 * AirbitzCore (ABC). It indicates a wide range of errors, from server issues
 * to bad passwords.
 */
public class AirbitzException extends Exception {
    private tABC_CC mCode;
    private tABC_Error mError;
    String mOtpResetToken;
    String mOtpResetDate;
    int mWaitSeconds;

    protected AirbitzException(tABC_CC code, tABC_Error error) {
        super(error.getSzDescription());
        mCode = code;
        mError = error;
    }
    public int code () { return mCode.swigValue(); }

    public String description () { return mCode.toString(); }

    public boolean isBadPassword() {
        return mCode == tABC_CC.ABC_CC_BadPassword;
    }

    public boolean isOtpError() {
        return mCode == tABC_CC.ABC_CC_InvalidOTP;
    }

    public boolean isAccountAlreadyExists() {
        return mCode == tABC_CC.ABC_CC_AccountAlreadyExists;
    }

    public boolean isAccountDoesNotExist() {
        return mCode == tABC_CC.ABC_CC_AccountDoesNotExist;
    }

    public boolean isWalletAlreadyExists() {
        return mCode == tABC_CC.ABC_CC_WalletAlreadyExists;
    }

    public boolean isInvalidWalletId() {
        return mCode == tABC_CC.ABC_CC_InvalidWalletID;
    }

    public boolean isUrlError() {
        return mCode == tABC_CC.ABC_CC_URLError;
    }

    public boolean isServerError() {
        return mCode == tABC_CC.ABC_CC_ServerError;
    }

    public boolean isNoRecoveryQuestions() {
        return mCode == tABC_CC.ABC_CC_NoRecoveryQuestions;
    }

    public boolean isNotSupported() {
        return mCode == tABC_CC.ABC_CC_NotSupported;
    }

    public boolean isInsufficientFunds() {
        return mCode == tABC_CC.ABC_CC_InsufficientFunds;
    }

    public boolean isSpendDust() {
        return mCode == tABC_CC.ABC_CC_SpendDust;
    }

    public boolean isSynchronizing() {
        return mCode == tABC_CC.ABC_CC_Synchronizing;
    }

    public boolean isNonNumericPin() {
        return mCode == tABC_CC.ABC_CC_NonNumericPin;
    }

    public boolean isInvalidPinWait() {
        return mCode == tABC_CC.ABC_CC_InvalidPinWait;
    }

    public String otpResetDate() {
        return mOtpResetDate;
    }

    public String otpResetToken() {
        return mOtpResetToken;
    }

    public int waitSeconds() {
        return mWaitSeconds;
    }

}
