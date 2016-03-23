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

import co.airbitz.internal.SWIGTYPE_p_void;
import co.airbitz.internal.tABC_QuestionChoice;

/**
 * QuestionChoice includes a recovery question as well as information regarding
 * the format of the answer to that question. Questions should match the
 * minimum length and developers are encouraged to ensure a user answers 2
 * questions from each question category.
 */
public class QuestionChoice extends tABC_QuestionChoice {
    String mQuestion = null;
    String mCategory = null;
    long mMinLength = -1;

    public QuestionChoice(SWIGTYPE_p_void pv) {
        super(PVoidStatic.getPtr(pv), false);
        if(PVoidStatic.getPtr(pv)!=0) {
            mQuestion = super.getSzQuestion();
            mCategory = super.getSzCategory();
            mMinLength = super.getMinAnswerLength();
        }
    }

    /**
     * Retrieve the question the user should answer
     * @return the question
     */
    public String question() {
        return mQuestion;
    }

    /**
     * Indicates the minimum length for an answer.
     * @return the minimum length for the answer.
     */
    public long minLength() {
        return mMinLength;
    }

    /**
     * Return the category of the question, will be either "string", "numeric" or "must".
     * @return the category of the question
     */
    public String category() {
        return mCategory;
    }

    private static class PVoidStatic extends SWIGTYPE_p_void {
        public static long getPtr(SWIGTYPE_p_void p) { return getCPtr(p); }
    }
}
