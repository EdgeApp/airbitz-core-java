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

import co.airbitz.internal.tABC_TxDetails;

/**
 * MetadataSet is a bunch of getters and setters that change the metadata of a
 * particular object. Those objects can be {@link ReceiveAddress}, {@link
 * Transaction} and {@link Spend}.
 */
public class MetadataSet {
    private static String TAG = MetadataSet.class.getSimpleName();

    private String mName;
    private double mFiat;
    private String mNotes;
    private long mBizid;
    private String mCategory;

    OnChangeListener mChangeListener;
    interface OnChangeListener {
        public void onChange();
    };

    /**
     * Setter for payee name.
     */
    public MetadataSet name(String name) {
        mName = name;
        if (mChangeListener != null) {
            mChangeListener.onChange();
        }
        return this;
    }

    /**
     * Getter for payee name.
     */
    public String name() {
        return mName;
    }

    /**
     * Setter for fiat amount.
     */
    public MetadataSet fiat(double fiat) {
        mFiat = fiat;
        if (mChangeListener != null) {
            mChangeListener.onChange();
        }
        return this;
    }

    /**
     * Getter for fiat amount.
     */
    public double fiat() {
        return mFiat;
    }

    /**
     * Setter for the notes.
     */
    public MetadataSet notes(String notes) {
        mNotes = notes;
        if (mChangeListener != null) {
            mChangeListener.onChange();
        }
        return this;
    }

    /**
     * Getter for the notes.
     */
    public String notes() {
        return mNotes;
    }

    /**
     * Setter for the Airbitz business id.
     */
    public MetadataSet bizid(long bizid) {
        mBizid = bizid;
        if (mChangeListener != null) {
            mChangeListener.onChange();
        }
        return this;
    }

    /**
     * Getter for the Airbitz business id.
     */
    public long bizid() {
        return mBizid;
    }

    /**
     * Setter for the category.
     */
    public MetadataSet category(String category) {
        mCategory = category;
        if (mChangeListener != null) {
            mChangeListener.onChange();
        }
        return this;
    }

    /**
     * Getter for the category.
     */
    public String category() {
        return mCategory;
    }

    tABC_TxDetails toTxDetails() {
        tABC_TxDetails details = new tABC_TxDetails();
        details.setSzName(name());
        details.setSzNotes(notes());
        details.setSzCategory(category());
        details.setBizId(bizid());
        details.setAmountCurrency(fiat());
        return details;
    }
}
