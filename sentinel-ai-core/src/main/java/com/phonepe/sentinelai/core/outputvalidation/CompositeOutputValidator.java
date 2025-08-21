package com.phonepe.sentinelai.core.outputvalidation;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import lombok.NonNull;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Can be used as a wrapper to do multiple validations.
 */
@Value
public class CompositeOutputValidator<R,T> implements OutputValidator<R,T> {
    List<OutputValidator<R,T>> validators;

    public CompositeOutputValidator() {
        this(List.of());
    }

    public CompositeOutputValidator(@NonNull Collection<OutputValidator<R,T>> validators) {
        this.validators = new ArrayList<>(validators);
    }

    public CompositeOutputValidator<R,T> addValidator(final OutputValidator<R,T> outputValidator) {
        validators.add(outputValidator);
        return this;
    }

    @Override
    public OutputValidationResults validate(AgentRunContext<R> context, T modelOutput) {
        return new OutputValidationResults(
                validators.stream()
                        .map(validator -> validator.validate(context, modelOutput))
                        .flatMap(output -> output.getFailures().stream())
                        .toList());
    }
}
