package com.ecm.core.exception;

import java.util.List;

public class ModelValidationException extends IllegalArgumentException {

    private final List<String> violations;

    public ModelValidationException(String message, List<String> violations) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
