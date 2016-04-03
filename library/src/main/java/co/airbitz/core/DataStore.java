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

import android.content.Context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_int;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_unsigned_int;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

/**
 * The DataStore object implements the Airbitz auto-encrypted, auto-backed up, and auto
 * synchronized Edge Security data storage. DataStore is end-to-end encrypted with no access to the
 * data by Airbitz, other users, or developers. Data is encrypted<br>
 * <br>
 * Data is saved as key/value pairs in named folders. Usage is as simple as calling
 * {@link #set set} to write data to this DataStore and calling {@link #get get} to read the data back.<br>
 * <br>
 * DataStore will automatically
 * backup all data and synchronize between all user's devices as long as the devices are
 * online. If devices are offline, the data will sync as soon as the device comes back online
 */
public class DataStore  {
    private static String TAG = DataStore.class.getSimpleName();

    private Account mAccount;
    private String mPluginId;

    DataStore(Account account, String pluginId) {
        mAccount = account;
        mPluginId = pluginId;
    }

    /**
     * List the keys in the data store.
     * @return the list of keys.
     */
    public List<String> list() {
        tABC_Error Error = new tABC_Error();
        List<String> keys = new ArrayList<String>();
        
        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int pUCount = core.int_to_uint(pCount);
        
        SWIGTYPE_p_long aKeys = core.new_longp();
        SWIGTYPE_p_p_p_char pppKeys = core.longp_to_pppChar(aKeys);
        
        tABC_CC result = core.ABC_PluginDataKeys(mAccount.username(),
                                                 mAccount.password(),
                                                 mPluginId,
                                                 pppKeys, pUCount, Error);
        if (tABC_CC.ABC_CC_Ok == result)
        {
            if (core.longp_value(aKeys) != 0)
            {
                int count = core.intp_value(pCount);
                long base = core.longp_value(aKeys);
                for (int i = 0; i < count; i++)
                {
                    Jni.pLong temp = new Jni.pLong(base + i * 4);
                    long start = core.longp_value(temp);
                    if (start != 0) {
                        keys.add(Jni.getStringAtPtr(start));
                    }
                }
            }
        }
        return keys;
    }
    
    /**
     * Retreive the value for a given key.
     * @param key the key
     * @return the value of the key
     */
    public String get(String key) {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_char ppChar = core.longp_to_ppChar(lp);

        core.ABC_PluginDataGet(
            mAccount.username(), mAccount.password(),
            mPluginId, key, ppChar, error);
        if (error.getCode() == tABC_CC.ABC_CC_Ok) {
            return Jni.getStringAtPtr(core.longp_value(lp));
        } else {
            return null;
        }
    }

    /**
     * Set the value for a given key.
     * @param key the key
     * @param value the value
     * @return true if successfully stored the key value pair, false otherwise
     */
    public boolean set(String key, String value) {
        tABC_Error error = new tABC_Error();
        core.ABC_PluginDataSet(
            mAccount.username(), mAccount.password(),
            mPluginId, key, value, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    /**
     * Remove a given key.
     * @param key the key
     * @return true if successfully removed the key value pair, false otherwise
     */
    public boolean remove(String key) {
        tABC_Error error = new tABC_Error();
        core.ABC_PluginDataRemove(
            mAccount.username(), mAccount.password(),
            mPluginId, key, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }

    /**
     * Clear the datastore of all its values
     * @return true if successfully cleared the data store
     */
    public boolean removeAll() {
        tABC_Error error = new tABC_Error();
        core.ABC_PluginDataClear(
            mAccount.username(), mAccount.password(),
            mPluginId, error);
        return error.getCode() == tABC_CC.ABC_CC_Ok;
    }
}
