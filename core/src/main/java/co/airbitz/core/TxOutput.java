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
import co.airbitz.internal.SWIGTYPE_p_int64_t;
import co.airbitz.internal.tABC_TxOutput;

/**
 * TxOutput contains information about an input or output of a transaction. It
 * is accessed from {@link Transaction #inputs()} or {@link Transaction
 * #outputs()}.
 */
public class TxOutput {
    private boolean mInput;
    private long  mIndex;
    private long  mValue;
    private String mAddress;

    public TxOutput(long pv) {
        tABC_TxOutput output = Jni.newTxOutput(pv);
        if (pv != 0) {
            mInput = output.getInput();
            mAddress = output.getSzAddress();
            mValue = Jni.get64BitLongAtPtr(Jni.getCPtr(output.getValue()));
        }
    }

    /**
     * Retrieve whether this is an input or an output.
     * @return true if this is an input, false otherwise
     */
    public boolean isInput() {
        return mInput;
    }

    /**
     * Retrieve the amount of the input or output.
     * @return the amount of the input or output
     */
    public long amount() {
        return mValue;
    }

    /**
     * Retrieve the address of the input or output.
     * @return the address of the input or output
     */
    public String address() {
        return mAddress;
    }

    /**
     * Retrieve the index of the input
     * @return the index of the input
     */
    public long index() {
        return mIndex;
    }
}
