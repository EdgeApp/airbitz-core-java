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

import java.util.ArrayList;
import java.util.List;

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_int;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_p_char;
import co.airbitz.internal.SWIGTYPE_p_unsigned_int;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;

/**
 * Categories provides accessor functions into a user's category list. It can
 * be access from {@link Account#categories categories()}.
 */
public class Categories {
    private static String TAG = Categories.class.getSimpleName();

    private Account mAccount;

    Categories(Account account) {
        this.mAccount = account;
    }

    /**
     * Retrieve a list of the user's categories
     * @return a list of the user's categories
     */
    public List<String> list() {
        List<String> categories = new ArrayList<String>();

        // get the categories from the core
        tABC_Error Error = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_char aszCategories = core.longp_to_pppChar(lp);

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int pUCount = core.int_to_uint(pCount);

        tABC_CC result = core.ABC_GetCategories(
                mAccount.username(), mAccount.password(),
                aszCategories, pUCount, Error);

        if (result!=tABC_CC.ABC_CC_Ok) {
            AirbitzCore.loge("loadCategories failed:"+Error.getSzDescription());
        }

        int count = core.intp_value(pCount);
        long base = core.longp_value(lp);
        for (int i = 0; i < count; i++) {
            Jni.pLong temp = new Jni.pLong(base + i * 4);
            long start = core.longp_value(temp);
            categories.add(Jni.getStringAtPtr(start));
        }
        return categories;
    }

    /**
     * Insert a new category into the user's list
     * @param category the new category to insert
     */
    public void insert(String category) {
        List<String> categories = list();
        if (categories != null && !categories.contains(category)) {
            // add the category to the core
            AirbitzCore.logi("Adding category: " + category);
            tABC_Error Error = new tABC_Error();
            core.ABC_AddCategory(
                    mAccount.username(), mAccount.password(),
                    category, Error);
        }
    }

    /**
     * Insert an array of new categories into the user's list.
     * @param categories the new categories to insert
     */
    public void insert(String[] categories) {
        for (String c : categories) {
            insert(c);
        }
    }

    /**
     * Remove a category from the user's categories.
     * @param category the category to remote
     */
    public boolean remove(String category) {
        AirbitzCore.logi("Remove category: " + category);
        tABC_Error Error = new tABC_Error();
        tABC_CC result = core.ABC_RemoveCategory(
                mAccount.username(), mAccount.password(),
                category, Error);
        return result == tABC_CC.ABC_CC_Ok;
    }
}
