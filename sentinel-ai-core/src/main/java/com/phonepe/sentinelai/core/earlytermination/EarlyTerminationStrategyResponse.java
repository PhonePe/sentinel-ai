/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.core.earlytermination;

import com.phonepe.sentinelai.core.errors.ErrorType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;


@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EarlyTerminationStrategyResponse {

    public enum ResponseType {
        /**
         * Terminate the model run early. The model layer should stop the run and return
         * the error type and reason to the caller.
         */
        TERMINATE,
        /**
         * Continue the model run.
         */
        CONTINUE,
        /**
         * Send the feedback message in the response back to the model as a user message,
         * and continue the run. Use this instead of {@link #TERMINATE} when the model can
         * recover from the detected condition if it receives an instruction.
         */
        INSTRUCT
    }

    ResponseType responseType;

    ErrorType errorType;

    String reason;

    public static EarlyTerminationStrategyResponse doNotTerminate() {
        return new EarlyTerminationStrategyResponse(ResponseType.CONTINUE,
                                                    ErrorType.SUCCESS,
                                                    ErrorType.SUCCESS
                                                            .getMessage());
    }

    /**
     * Ask the model layer to send the given instruction text to the model as a user
     * message and continue the run.
     *
     * @param instruction Text to send to the model. The model layer must add this text
     *                    to the conversation before the next model call.
     * @return Response of type {@link ResponseType#INSTRUCT}
     */
    public static EarlyTerminationStrategyResponse instructWithFeedback(String instruction) {
        return new EarlyTerminationStrategyResponse(ResponseType.INSTRUCT,
                                                    ErrorType.SUCCESS,
                                                    instruction);
    }

    public static EarlyTerminationStrategyResponse terminate(ErrorType errorType,
                                                             String reason) {
        return new EarlyTerminationStrategyResponse(ResponseType.TERMINATE,
                                                    errorType,
                                                    reason);
    }
}
