package co.airbitz.AirbitzCoreRCT;

/**
 * Created by paul on 7/22/16.
 */

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

import android.content.Context;
import android.widget.Toast;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
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
import java.util.Map;

public class AirbitzCoreRCT extends ReactContextBaseJavaModule {

    private Context mContext    = null;
    private AirbitzCore mABC    = null;
    private Account mABCAccount  = null;

    @Override
    public String getName() {
        return "AirbitzCoreRCT";
    }

    public AirbitzCoreRCT(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @ReactMethod
    public void init(String abcAPIKey,
                     String hbitsKey,
                     Callback complete,
                     Callback error) {
        if (mContext == null)
            mContext = getReactApplicationContext().getBaseContext();

        if (mABC != null) {
            error.invoke(null, makeError(23, "ABC Already Initialized"));
        } else {
            mABC = AndroidUtils.init(mContext, abcAPIKey, hbitsKey);
        }

        if (mABC != null)
            complete.invoke();
        else
            error.invoke(null);
    }

    @ReactMethod
    public void createAccount(String username,
                              String password,
                              String pin,
                              Callback complete,
                              Callback error) {
        if (mABC == null) {
            error.invoke(null, makeErrorABCNotInitialized());
            return;
        }

        if (mABCAccount != null) {
            mABCAccount.logout();
            mABCAccount = null;
        }

        try {
            mABCAccount =  mABC.createAccount(username, password, pin);
            complete.invoke(null, mABCAccount.username());
            return;
        } catch (AirbitzException e) {
            error.invoke(null, makeError(e.code(), e.description()));
            return;
        }
    }

    class ABCError {
        int code;
        String description;
        String description2;
        String description3;
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
