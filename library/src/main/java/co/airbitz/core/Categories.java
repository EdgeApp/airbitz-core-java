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

public class Categories {
    private static String TAG = Categories.class.getSimpleName();

    private Account mAccount;

    Categories(Account account) {
        this.mAccount = account;
    }

    private boolean isValidCategory(String category) {
        return category.startsWith("Expense") || category.startsWith("Exchange") ||
                category.startsWith("Income") || category.startsWith("Transfer");
    }

    public List<String> loadCategories() {
        List<String> categories = new ArrayList<String>();

        // get the categories from the core
        tABC_Error Error = new tABC_Error();

        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_p_char aszCategories = core.longp_to_pppChar(lp);

        SWIGTYPE_p_int pCount = core.new_intp();
        SWIGTYPE_p_unsigned_int pUCount = core.int_to_uint(pCount);

        tABC_CC result = core.ABC_GetCategories(
                mAccount.getUsername(), mAccount.getPassword(),
                aszCategories, pUCount, Error);

        if (result!=tABC_CC.ABC_CC_Ok) {
            AirbitzCore.debugLevel(1, "loadCategories failed:"+Error.getSzDescription());
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

    public void addCategory(String strCategory) {
        List<String> categories = loadCategories();
        if (categories != null && !categories.contains(strCategory)) {
            // add the category to the core
            AirbitzCore.debugLevel(1, "Adding category: "+strCategory);
            tABC_Error Error = new tABC_Error();
            core.ABC_AddCategory(
                    mAccount.getUsername(), mAccount.getPassword(),
                    strCategory, Error);
        }
    }

    public boolean removeCategory(String strCategory) {
        AirbitzCore.debugLevel(1, "Remove category: "+strCategory);
        tABC_Error Error = new tABC_Error();
        tABC_CC result = core.ABC_RemoveCategory(
                mAccount.getUsername(), mAccount.getPassword(),
                strCategory, Error);
        return result==tABC_CC.ABC_CC_Ok;
    }
}
