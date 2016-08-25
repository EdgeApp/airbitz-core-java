package co.airbitz.AirbitzCoreRCT;

/**
 * Created by paul on 7/22/16.
 */

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import android.content.Context;
import android.telecom.Call;
import android.widget.Toast;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.gson.Gson;

import org.json.JSONObject;
import org.json.JSONStringer;

import co.airbitz.core.Account;
import co.airbitz.core.AirbitzCore;
import co.airbitz.core.AirbitzException;
import co.airbitz.core.Categories;
import co.airbitz.core.CoreCurrency;
import co.airbitz.core.Settings;
import co.airbitz.core.Transaction;
import co.airbitz.core.Utils;
import co.airbitz.core.Wallet;
import co.airbitz.core.android.AndroidUtils;

import java.lang.reflect.Array;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class AirbitzCoreRCT extends ReactContextBaseJavaModule {

    private Context mContext    = null;
    private AirbitzCore mABC    = null;
    private Account mABCAccount  = null;
    private ReactApplicationContext mReactContext = null;


    @Override
    public String getName() {
        return "AirbitzCoreRCT";
    }

    public AirbitzCoreRCT(ReactApplicationContext reactContext) {
        super(reactContext);
        mReactContext = reactContext;
    }

    /***********************************************
     * ABCContext methods
     ***********************************************/

    @ReactMethod
    public void init(String abcAPIKey,
                     String hbitsKey,
                     Callback callback) {
        if (mContext == null)
            mContext = getReactApplicationContext().getBaseContext();

        if (mABC != null) {
            callback.invoke(makeError(23, "ABC Already Initialized"));
            return;
        } else {
            mABC = AndroidUtils.init(mContext, abcAPIKey, hbitsKey);
        }

        if (mABC != null)
            callback.invoke(null, null);
        else
            callback.invoke(makeError(1, "Error Initializing ABC"));
    }

    @ReactMethod
    public void createAccount(String username,
                              String password,
                              String pin,
                              Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized());
            return;
        }

        if (mABCAccount != null) {
            mABCAccount.logout();
            mABCAccount = null;
        }

        try {
            if (password != null && password.length() == 0)
                password = null;
            mABCAccount =  mABC.createAccount(username, password, pin);
            registerCallbacks(mABCAccount);
            callback.invoke(null, mABCAccount.username());
            return;
        } catch (AirbitzException e) {
            callback.invoke(makeError(e.code(), e.description()));
            return;
        }
    }

    @ReactMethod
    public void loginWithPassword(String username,
                                  String password,
                                  String otpToken,
                                  Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized());
            return;
        }

        if (mABCAccount != null) {
            mABCAccount.logout();
            mABCAccount = null;
        }

        try {
            mABCAccount = mABC.passwordLogin(username, password, otpToken);
            registerCallbacks(mABCAccount);
            callback.invoke(null, mABCAccount.username());
        } catch (AirbitzException e) {
            if (e.code() == 37 /* ABCConditionCodeInvalidOTP */) {
                String err = makeOTPError(e.code(), e.otpResetDate(), e.otpResetToken());
                callback.invoke(err);
            } else {
                callback.invoke(makeError(e.code(), e.description()));
            }
            return;
        }
    }

    @ReactMethod
    public void loginWithPIN(String username,
                             String pin,
                             Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(), null);
            return;
        }

        if (mABCAccount != null) {
            mABCAccount.logout();
            mABCAccount = null;
        }

        try {
            mABCAccount = mABC.pinLogin(username, pin, null);
            registerCallbacks(mABCAccount);

            callback.invoke(null, mABCAccount.username());
        } catch (AirbitzException e) {
            callback.invoke(makeError(e.code(), e.description()), null);
            return;
        }
    }

    @ReactMethod
    public void accountHasPassword(String username,
                                   Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(), null);
            return;
        }

        Boolean hasPassword = mABC.accountHasPassword(username);
        callback.invoke(null, hasPassword);
    }

    @ReactMethod
    public void deleteLocalAccount(String username,
                                   Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(), null);
            return;
        }

        mABC.accountDeleteLocal(username);
        callback.invoke(null, null);
    }

    @ReactMethod
    public void listUsernames(Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(), null);
            return;
        }

        List<String> usernames = mABC.listLocalAccounts();
        WritableArray arrayNames = Arguments.createArray();

        for (int i = 0; i < usernames.size(); i++)
            arrayNames.pushString(usernames.get(i));
        callback.invoke(null, arrayNames);
    }

    @ReactMethod
    public void usernameAvailable(String username,
                                  Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(), null);
            return;
        }

        try {
            Boolean available = mABC.isUsernameAvailable(username);
            callback.invoke(null, available);
        } catch (AirbitzException e) {
            callback.invoke(null, false);
            return;
        }
    }

    @ReactMethod
    public void pinLoginEnabled(String username,
                                Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(), null);
            return;
        }

        Boolean enabled = mABC.accountHasPinLogin(username);
        callback.invoke(null, enabled);
    }
    /***********************************************
     * ABCAccount methods
     ***********************************************/

    @ReactMethod
    public void changePassword(String password,
                               Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(), null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(), null);
            return;
        }

        try {
            mABCAccount.passwordChange(password);
            callback.invoke(null, null);
        } catch (AirbitzException e) {
            callback.invoke(makeError(e.code(), e.description()), null);
            return;
        }
    }

    @ReactMethod
    public void changePIN(String pin,
                          Callback callback) {
        if (mABC == null) {
            callback.invoke(null, makeErrorABCNotInitialized(), null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(), null);
            return;
        }

        try {
            mABCAccount.pin(pin);
            callback.invoke(null, null);
        } catch (AirbitzException e) {
            callback.invoke(null, makeError(e.code(), e.description()));
            return;
        }
    }

    @ReactMethod
    public void checkPassword(String password,
                              Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }

        Boolean check = mABCAccount.checkPassword(password);
        callback.invoke(null, check);
    }

    @ReactMethod
    public void enablePINLogin(Boolean enable,
                               Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        try {
            mABCAccount.pinLoginSetup(enable);
            callback.invoke(null, null);
        } catch (AirbitzException e) {
            callback.invoke(makeError(e.code(), e.description()),null);
            return;
        }
    }


    @ReactMethod
    public void logout(Callback complete) {
        if (mABC != null) {
            if (mABCAccount != null) {
                mABCAccount.logout();
                mABCAccount = null;
            }
        }
        complete.invoke();
    }

    @ReactMethod
    public void setupOTPKey(String key,
                            Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        try {
            mABCAccount.otpSecret(key);
            callback.invoke(null, null);
        } catch (AirbitzException e) {
            callback.invoke(makeError(e.code(), e.description()),null);
            return;
        }
    }

    @ReactMethod
    public void getOTPLocalKey(Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        String key = mABCAccount.otpSecret();
        callback.invoke(null, key);
    }

    @ReactMethod
    public void getOTPDetails(Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        try {
            Boolean enabled = mABCAccount.isOtpEnabled();
            callback.invoke(null, enabled, 60 * 60 * 24 * 7);
        } catch (AirbitzException e) {
            callback.invoke(makeError(e.code(), e.description()),null);
            return;
        }
    }

    @ReactMethod
    public void enableOTP(int timeout,
                          Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        try {
            mABCAccount.otpEnable(timeout);
            callback.invoke(null, null);
        } catch (AirbitzException e) {
            callback.invoke(makeError(e.code(), e.description()),null);
            return;
        }
    }

    @ReactMethod
    public void disableOTP (Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        try {
            mABCAccount.otpDisable();
            callback.invoke(null, null);
        } catch (AirbitzException e) {
            callback.invoke(makeError(e.code(), e.description()),null);
            return;
        }
    }

    @ReactMethod
    public void cancelOTPResetRequest (Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        try {
            mABCAccount.otpResetCancel();
            callback.invoke(null, null);
        } catch (AirbitzException e) {
            callback.invoke(makeError(e.code(), e.description()),null);
            return;
        }
    }

    @ReactMethod
    public void signBitIDRequest(String uri,
                                 String message,
                                 Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        Account.BitidSignature bitidSignature =  mABCAccount.bitidSign(uri, message);

        WritableMap bitidMap = Arguments.createMap();

        bitidMap.putString("signature", bitidSignature.signature);
        bitidMap.putString("address", bitidSignature.address);

        callback.invoke(null, bitidMap);
    }

    private void registerCallbacks(Account account) {
        account.callbacks(new Account.Callbacks() {
            public void remotePasswordChange() {
                AirbitzCore.logw("callback: remotePasswordChange");
            }

            public void loggedOut() {
                AirbitzCore.logw("callback: loggedOut");
            }

            public void accountChanged() {
                AirbitzCore.logw("callback: accountChanged");
                if (mABCAccount != null) {
                    WritableMap map = Arguments.createMap();
                    map.putString("name", mABCAccount.username());
                    getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("abcAccountAccountChanged", map);
                }
            }

            public void walletsLoading() {
                AirbitzCore.logw("callback: walletsLoading");
            }

            public void walletChanged(Wallet wallet) {
                AirbitzCore.logw("callback: walletChanged");
                if (mABCAccount != null) {
                    WritableMap map = Arguments.createMap();
                    map.putString("uuid", wallet.id());
                    getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("abcAccountAccountChanged", map);
                }
            }

            public void walletsLoaded() {
                AirbitzCore.logw("callback: walletsLoaded");
                if (mABCAccount != null) {
                    WritableMap map = Arguments.createMap();
                    getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("abcAccountWalletsLoaded", map);
                }
            }

            public void walletsChanged() {
                AirbitzCore.logw("callback: walletsChanged");
            }

            public void otpSkew() {
                AirbitzCore.logw("callback: otpSkew");
            }

            public void otpRequired() {
                AirbitzCore.logw("callback: otpRequired");
            }

            public void otpResetPending() {
                AirbitzCore.logw("callback: otpResetPending");
            }

            public void exchangeRateChanged() {
                AirbitzCore.logw("callback: exchangeRateChanged");
            }

            public void blockHeightChanged() {
                AirbitzCore.logw("callback: blockHeightChanged");
            }

            public void balanceUpdate(final Wallet wallet, final Transaction tx) {
                AirbitzCore.logw("callback: balanceUpdate");
            }

            public void incomingBitcoin(final Wallet wallet, final Transaction tx) {
                AirbitzCore.logw("callback: incomingBitcoin");
            }

            public void sweep(final Wallet wallet, final Transaction tx, final long amountSwept) {
                AirbitzCore.logw("callback: sweep");
            }
        });
        account.startBackgroundTasks();
    }

    /***********************************************
     * ABCDataStore methods
     ***********************************************/

    @ReactMethod
    public void writeData(String folder,
                          String key,
                          String value,
                          Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        mABCAccount.data(folder).set(key, value);
        callback.invoke(null, null);
    }

    @ReactMethod
    public void readData(String folder,
                         String key,
                         Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        String value = mABCAccount.data(folder).get(key);
        callback.invoke(null, value);
    }

    @ReactMethod
    public void removeDataKey(String folder,
                              String key,
                              Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        mABCAccount.data(folder).remove(key);
        callback.invoke(null, null);
    }

//    @ReactMethod
//    public void listDataKeys(String folder,
//                             Callback callback) {
//        if (mABC == null) {
//            callback.invoke(makeErrorABCNotInitialized(),null);
//            return;
//        }
//
//        if (mABCAccount == null) {
//            callback.invoke(makeErrorNotLoggedIn(),null);
//            return;
//        }
//        WritableArray keys = Arguments.createArray();
//
//        mABCAccount.data(folder).
//        keys.pushString();
//        bitidMap.putString("signature", bitidSignature.signature);
//        bitidMap.putString("address", bitidSignature.address);
//
//        callback.invoke(null, bitidMap);
//    }

    @ReactMethod
    public void removeDataFolder(String folder,
                                 Callback callback) {
        if (mABC == null) {
            callback.invoke(makeErrorABCNotInitialized(),null);
            return;
        }

        if (mABCAccount == null) {
            callback.invoke(makeErrorNotLoggedIn(),null);
            return;
        }
        mABCAccount.data(folder).removeAll();
        callback.invoke(null, null);
    }

    //
    // To standardize between React Native on ObjC and Android, all methods use one method of type Callback.
    // The first parameter is always an Error object which is null if method succeeds.
    //


    class ABCError {
        int code;
        String description;
        String description2;
        String description3;
        String resetDate;
        String resetToken;
    }

    private String makeErrorABCNotInitialized () {
        return makeError(22, "ABC Not Initialized");
    }

    private String makeErrorNotLoggedIn () {
        return makeError(1, "Not logged in");
    }

    private String makeError(int code,
                             String description) {
        return makeError(code, description, null, null);
    }

    private String makeError(int code,
                             String description,
                             String description2) {
        return makeError(code, description, description2, null);
    }

    private String makeOTPError(int code,
                                String otpResetDate,
                                String otpResetToken) {


        ABCError err = new ABCError();

        err.code = code;
        if (otpResetDate != null)
            err.resetDate = otpResetDate;
        if (otpResetToken != null)
            err.resetToken = otpResetToken;

        return makeJsonFromObj(err);
    }

    private String makeError(int code,
                             String description,
                             String description2,
                             String description3) {
        ABCError err = new ABCError();

        err.code = code;
        if (description != null)
            err.description = description;
        if (description2 != null)
            err.description2 = description2;
        if (description3 != null)
            err.description3 = description3;

        return makeJsonFromObj(err);
    }

    private String makeJsonFromObj(Object object) {
        Gson gson = new Gson();
        return gson.toJson(object);
    }
}
