package com.ecm.core.service;

import com.ecm.core.entity.ConstraintDefinition;
import com.ecm.core.entity.ConstraintType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Component
public class PropertyConstraintValidator {

    public List<String> validate(Object value, List<ConstraintDefinition> constraints) {
        if (value == null || constraints == null || constraints.isEmpty()) {
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        for (ConstraintDefinition c : constraints) {
            String msg = switch (c.getConstraintType()) {
                case REGEX -> validateRegex(value, c.getParameters());
                case LIST -> validateList(value, c.getParameters());
                case RANGE -> validateRange(value, c.getParameters());
                case LENGTH -> validateLength(value, c.getParameters());
            };
            if (msg != null) {
                violations.add(msg);
            }
        }
        return violations;
    }

    String validateRegex(Object value, Map<String, Object> params) {
        if (value == null || params == null) {
            return null;
        }
        Object exprObj = params.get("expression");
        if (exprObj == null) {
            return null;
        }
        String expression = exprObj.toString();
        String strValue = value.toString();
        try {
            if (!Pattern.matches(expression, strValue)) {
                return "Value '" + strValue + "' does not match pattern '" + expression + "'";
            }
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex expression '{}': {}", expression, e.getMessage());
            return "Invalid constraint regex: " + expression;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    String validateList(Object value, Map<String, Object> params) {
        if (value == null || params == null) {
            return null;
        }
        Object allowedObj = params.get("allowedValues");
        if (allowedObj == null) {
            return null;
        }
        try {
            List<String> allowedValues = (List<String>) allowedObj;
            String strValue = value.toString();
            if (!allowedValues.contains(strValue)) {
                return "Value '" + strValue + "' is not in the allowed list " + allowedValues;
            }
        } catch (ClassCastException e) {
            log.warn("allowedValues parameter is not a List<String>: {}", e.getMessage());
            return "Invalid constraint configuration for LIST constraint";
        }
        return null;
    }

    String validateRange(Object value, Map<String, Object> params) {
        if (value == null || params == null) {
            return null;
        }
        double numericValue;
        try {
            numericValue = Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return "Value '" + value + "' is not a valid number for range check";
        }
        Object minObj = params.get("minValue");
        Object maxObj = params.get("maxValue");
        if (minObj instanceof Number minValue) {
            if (numericValue < minValue.doubleValue()) {
                return "Value " + numericValue + " is less than minimum " + minValue;
            }
        }
        if (maxObj instanceof Number maxValue) {
            if (numericValue > maxValue.doubleValue()) {
                return "Value " + numericValue + " is greater than maximum " + maxValue;
            }
        }
        return null;
    }

    String validateLength(Object value, Map<String, Object> params) {
        if (value == null || params == null) {
            return null;
        }
        String strValue = value.toString();
        int length = strValue.length();
        Object minObj = params.get("minLength");
        Object maxObj = params.get("maxLength");
        if (minObj instanceof Integer minLength) {
            if (length < minLength) {
                return "Value length " + length + " is less than minimum length " + minLength;
            }
        }
        if (maxObj instanceof Integer maxLength) {
            if (length > maxLength) {
                return "Value length " + length + " exceeds maximum length " + maxLength;
            }
        }
        return null;
    }
}
