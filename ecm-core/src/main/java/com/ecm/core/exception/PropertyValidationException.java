package com.ecm.core.exception;

import java.util.List;

public class PropertyValidationException extends IllegalArgumentException {

    private final List<String> violations;

    public PropertyValidationException(String message, List<String> violations) {
        super(message);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
