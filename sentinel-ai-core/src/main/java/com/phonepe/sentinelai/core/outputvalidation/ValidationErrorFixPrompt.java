package com.phonepe.sentinelai.core.outputvalidation;

import lombok.Value;

/**
 * This prompt is used by Sentinel to instruct the model to fix validation errors
 */
@Value
public class ValidationErrorFixPrompt {
    String objective = "Fix the validation errors in the previously generate output";
    String validationError;
    String previouslyGeneratedOutput;
}
