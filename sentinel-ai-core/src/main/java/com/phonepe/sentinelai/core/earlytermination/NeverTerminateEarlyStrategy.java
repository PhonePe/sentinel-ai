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

import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;

/**
 * An early termination strategy that never terminates the model run early.
 */


public class NeverTerminateEarlyStrategy implements EarlyTerminationStrategy {
    @Override
    public EarlyTerminationStrategyResponse evaluate(final ModelSettings modelSettings, final ModelRunContext context,
            final ModelOutput output) {
        return EarlyTerminationStrategyResponse.doNotTerminate();
    }
}
