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
import co.airbitz.internal.SWIGTYPE_p_p_sABC_PaymentRequest;

/**
 * ParsedUri encapsulates the data of a bitcoin URI. It understands bitcoin
 * addresses, private keys in WIF format, Bitid requests and BIP70 requests. If
 * the parsed text is a BIP70 request, {@link ParsedUri#fetchPaymentRequest
 * fetchPaymentRequest} must be called as well to fetch the details of the
 * payment.
 */
public class ParsedUri {

    /**
     * The type of URI that was parsed such as bitcoin adddresses, private keys
     * (WIFs), Bitid URLs and BIP70 payment requests.
     */
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
            throw new AirbitzException(error.getCode(), error);
        }
        mParsedUri = Jni.newParsedUri(core.longp_value(lp));
        mMeta = new MetadataSet();
        mAddress = mParsedUri.getSzAddress();
        mWif = mParsedUri.getSzWif();
        mPaymentProto = mParsedUri.getSzPaymentProto();
        mBitidUri = mParsedUri.getSzBitidUri();
        if (mPaymentProto != null) {
            mType = UriType.PAYMENT_PROTO;
        } else if (mBitidUri != null) {
            mType = UriType.BITID;
        } else if (mWif != null) {
            mType = UriType.PRIVATE_KEY;
        } else if (mAddress != null) {
            mType = UriType.ADDRESS;
        }
    }

    /**
     * Returns the type of text was parsed.
     * @return the URI type
     */
    public UriType type() {
        return mType;
    }

    /**
     * Returns the metadata for this request
     * @return the metadata for this request
     */
    public MetadataSet meta() {
        return mMeta;
    }

    /**
     * Returns the bitcoin address for this URI. If the URI is not a bitcoin
     * address or WIF this will return null.
     * @return the address for this URI
     */
    public String address() {
        return mAddress;
    }

    /**
     * Returns the private key or WIF for this URI. If the URI is not a WIF
     * this will return null.
     * @return the WIF for this URI
     */
    public String privateKey() {
        return mWif;
    }

    /**
     * Returns the BIP70 URL. If the URI is not a BIP70 request this will
     * return null.
     * @return the BIP70 URL
     */
    public String paymentProto() {
        return mPaymentProto;
    }

    /**
     * Returns the Bitid URL. If the URI is not a Bitid request this will
     * return null.
     * @return the Bitid URL
     */
    public String bitid() {
        return mBitidUri;
    }

    /**
     * Fetches the {@link PaymentRequest} details from a server. This will
     * require network access.
     * @return payment request details for this BIP70 request
     */
    public PaymentRequest fetchPaymentRequest() throws AirbitzException {
        tABC_Error error = new tABC_Error();
        SWIGTYPE_p_long lp = core.new_longp();
        SWIGTYPE_p_p_sABC_PaymentRequest ppResult = core.longPtr_to_ppPaymentRequest(lp);
        core.ABC_FetchPaymentRequest(mPaymentProto, ppResult, error);
        if (error.getCode() != tABC_CC.ABC_CC_Ok) {
            throw new AirbitzException(error.getCode(), error);
        }
        return new PaymentRequest(Jni.newPaymentRequest(core.longp_value(lp)));
    }

    /**
     * Returns the amount parameter in the URI.
     * @return the amount for this request in satoshis
     */
    public long amount() {
        return Jni.get64BitLongAtPtr(
            Jni.getCPtr(mParsedUri.getAmountSatoshi()));
    }

    /**
     * Returns the label parameter specified in the URI.
     * @return the label for this URI.
     */
    public String label() {
        return mParsedUri.getSzLabel();
    }

    /**
     * Returns the message parameter specified in the URI.
     * @return the message for this URI.
     */
    public String message() {
        return mParsedUri.getSzMessage();
    }

    /**
     * Returns the category parameter specified in the URI.
     * @return the category for this URI.
     */
    public String category() {
        return mParsedUri.getSzCategory();
    }

    /**
     * Returns the return URI parameter specified in the URI.
     * @return the return URI for this URI.
     */
    public String returnUri() {
        return mParsedUri.getSzRet();
    }
}
