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

import co.airbitz.internal.Jni;
import co.airbitz.internal.SWIGTYPE_p_long;
import co.airbitz.internal.SWIGTYPE_p_p_sABC_ParsedUri;
import co.airbitz.internal.core;
import co.airbitz.internal.tABC_CC;
import co.airbitz.internal.tABC_Error;
import co.airbitz.internal.tABC_ParsedUri;

public class ParsedUri {
    public enum UriType {
        ADDRESS,
        PRIVATE_KEY,
        BITID,
        PAYMENT_PROTO;
    };

    private tABC_ParsedUri mParsedUri;
    private UriType mType;
    private MetadataSet mMeta;
    private String mAddress;
    private String mWif;
    private String mPaymentProto;
    private String mBitidUri;

    ParsedUri(String text) throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_sABC_ParsedUri ppResult = core.longPtr_to_ppParsedUri(lp);
        core.ABC_ParseUri(text, ppResult, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(null, error.getCode(), error);
        }
        mParsedUri = Jni.newParsedUri(core.longp_value(lp));
        mMeta = new MetadataSet();
        mAddress = mParsedUri.getSzAddress();
        mWif = mParsedUri.getSzWif();
        mPaymentProto = mParsedUri.getSzPaymentProto();
        mBitidUri = mParsedUri.getSzBitidUri();
        if (mAddress != null) {
            mType = UriType.ADDRESS;
        } else if (mWif != null) {
            mType = UriType.PRIVATE_KEY;
        } else if (mPaymentProto != null) {
            mType = UriType.PAYMENT_PROTO;
        } else if (mBitidUri != null) {
            mType = UriType.BITID;
        }
    }

    public UriType type() {
        return mType;
    }

    public MetadataSet meta() {
        return mMeta;
    }

    public String address() {
        return mAddress;
    }

    public String paymentProto() {
        return mPaymentProto;
    }

    public String bitid() {
        return mBitidUri;
    }

    public void paymentProtoFetch() throws AirbitzException {
        throw new AirbitzException(null, null, null);
    }
}
