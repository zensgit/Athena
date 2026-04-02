package com.ecm.core.service;

import com.ecm.core.entity.ConstraintDefinition;
import com.ecm.core.entity.ConstraintType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyConstraintValidatorTest {

    private PropertyConstraintValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PropertyConstraintValidator();
    }

    private ConstraintDefinition buildConstraint(ConstraintType type, Map<String, Object> parameters) {
        ConstraintDefinition constraint = new ConstraintDefinition();
        constraint.setId(UUID.randomUUID());
        constraint.setConstraintType(type);
        constraint.setParameters(parameters);
        return constraint;
    }

    @Nested
    class Regex {

        private ConstraintDefinition regexConstraint;

        @BeforeEach
        void setUp() {
            Map<String, Object> params = new HashMap<>();
            params.put("expression", "^[\\w.]+@[\\w.]+$");
            regexConstraint = buildConstraint(ConstraintType.REGEX, params);
        }

        @Test
        void validEmailPassesRegex() {
            List<String> violations = validator.validate("user@example.com", List.of(regexConstraint));

            assertThat(violations).isEmpty();
        }

        @Test
        void invalidEmailFailsRegex() {
            List<String> violations = validator.validate("not-an-email", List.of(regexConstraint));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).containsIgnoringCase("match");
        }

        @Test
        void nullValueReturnsNoViolations() {
            List<String> violations = validator.validate(null, List.of(regexConstraint));

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    class ListConstraint {

        private ConstraintDefinition listConstraint;

        @BeforeEach
        void setUp() {
            Map<String, Object> params = new HashMap<>();
            params.put("allowedValues", List.of("RED", "GREEN", "BLUE"));
            listConstraint = buildConstraint(ConstraintType.LIST, params);
        }

        @Test
        void valueInAllowedListPasses() {
            List<String> violations = validator.validate("RED", List.of(listConstraint));

            assertThat(violations).isEmpty();
        }

        @Test
        void valueNotInListFailsWithViolationMessage() {
            List<String> violations = validator.validate("YELLOW", List.of(listConstraint));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).contains("YELLOW");
        }
    }

    @Nested
    class Range {

        private ConstraintDefinition rangeConstraint;

        @BeforeEach
        void setUp() {
            Map<String, Object> params = new HashMap<>();
            params.put("minValue", 0);
            params.put("maxValue", 100);
            rangeConstraint = buildConstraint(ConstraintType.RANGE, params);
        }

        @Test
        void valueWithinRangePasses() {
            List<String> violations = validator.validate(50, List.of(rangeConstraint));

            assertThat(violations).isEmpty();
        }

        @Test
        void valueBelowMinFails() {
            List<String> violations = validator.validate(-1, List.of(rangeConstraint));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).containsIgnoringCase("min");
        }

        @Test
        void valueAboveMaxFails() {
            List<String> violations = validator.validate(101, List.of(rangeConstraint));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).containsIgnoringCase("max");
        }
    }

    @Nested
    class Length {

        private ConstraintDefinition lengthConstraint;

        @BeforeEach
        void setUp() {
            Map<String, Object> params = new HashMap<>();
            params.put("minLength", 2);
            params.put("maxLength", 50);
            lengthConstraint = buildConstraint(ConstraintType.LENGTH, params);
        }

        @Test
        void valueWithinLengthPasses() {
            List<String> violations = validator.validate("hello", List.of(lengthConstraint));

            assertThat(violations).isEmpty();
        }

        @Test
        void valueTooShortFails() {
            List<String> violations = validator.validate("a", List.of(lengthConstraint));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).containsIgnoringCase("minimum length");
        }

        @Test
        void valueTooLongFails() {
            String longValue = "a".repeat(51);
            List<String> violations = validator.validate(longValue, List.of(lengthConstraint));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).containsIgnoringCase("maximum length");
        }
    }

    @Nested
    class Mixed {

        @Test
        void multipleConstraintsAllValidated() {
            Map<String, Object> regexParams = new HashMap<>();
            regexParams.put("expression", "^[A-Z]+$");
            ConstraintDefinition regexConstraint = buildConstraint(ConstraintType.REGEX, regexParams);

            Map<String, Object> listParams = new HashMap<>();
            listParams.put("allowedValues", List.of("RED", "GREEN", "BLUE"));
            ConstraintDefinition listConstraint = buildConstraint(ConstraintType.LIST, listParams);

            // "YELLOW" matches the regex (all uppercase letters) but is not in the allowed list
            List<String> violations = validator.validate("YELLOW", List.of(regexConstraint, listConstraint));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).contains("YELLOW");
        }
    }
}
