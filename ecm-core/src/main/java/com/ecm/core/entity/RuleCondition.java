package com.ecm.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Rule Condition
 *
 * Represents a condition or a composite of conditions for automation rules.
 * Supports simple field comparisons and complex AND/OR/NOT combinations.
 *
 * Examples:
 *
 * Simple condition:
 * {
 *   "type": "SIMPLE",
 *   "field": "name",
 *   "operator": "contains",
 *   "value": "invoice"
 * }
 *
 * Composite AND condition:
 * {
 *   "type": "AND",
 *   "children": [
 *     {"type": "SIMPLE", "field": "mimeType", "operator": "equals", "value": "application/pdf"},
 *     {"type": "SIMPLE", "field": "size", "operator": "gt", "value": 1048576}
 *   ]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleCondition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Type of condition
     */
    private ConditionType type;

    /**
     * Field name for SIMPLE conditions
     * Supported fields: name, mimeType, size, content, createdBy, path
     * Also supports custom metadata fields via "metadata.fieldName"
     */
    private String field;

    /**
     * Comparison operator for SIMPLE conditions
     */
    private String operator;

    /**
     * Target value for comparison
     */
    private Object value;

    /**
     * Child conditions for composite types (AND, OR, NOT)
     */
    private List<RuleCondition> children;

    /**
     * Case-insensitive comparison (for string operations)
     * Default: true
     */
    @Builder.Default
    private Boolean ignoreCase = true;

    /**
     * Types of conditions
     */
    public enum ConditionType {
        /**
         * Simple condition: field operator value
         * Example: name contains "report"
         */
        SIMPLE,

        /**
         * All child conditions must match
         */
        AND,

        /**
         * At least one child condition must match
         */
        OR,

        /**
         * Negation of the first child condition
         */
        NOT,

        /**
         * Always true - useful for catch-all rules
         */
        ALWAYS_TRUE,

        /**
         * Always false - useful for disabled rules
         */
        ALWAYS_FALSE
    }

    /**
     * Available comparison operators
     */
    public static class Operators {
        public static final String EQUALS = "equals";
        public static final String NOT_EQUALS = "notEquals";
        public static final String CONTAINS = "contains";
        public static final String NOT_CONTAINS = "notContains";
        public static final String STARTS_WITH = "startsWith";
        public static final String ENDS_WITH = "endsWith";
        public static final String REGEX = "regex";
        public static final String GREATER_THAN = "gt";
        public static final String GREATER_THAN_EQUALS = "gte";
        public static final String LESS_THAN = "lt";
        public static final String LESS_THAN_EQUALS = "lte";
        public static final String IN = "in";
        public static final String NOT_IN = "notIn";
        public static final String IS_NULL = "isNull";
        public static final String IS_NOT_NULL = "isNotNull";
        public static final String IS_EMPTY = "isEmpty";
        public static final String IS_NOT_EMPTY = "isNotEmpty";
    }

    /**
     * Available field names for conditions
     */
    public static class Fields {
        public static final String NAME = "name";
        public static final String DESCRIPTION = "description";
        public static final String MIME_TYPE = "mimeType";
        public static final String SIZE = "size";
        public static final String CONTENT = "content";
        public static final String TEXT_CONTENT = "textContent";
        public static final String CREATED_BY = "createdBy";
        public static final String PATH = "path";
        public static final String PARENT_ID = "parentId";
        public static final String TAGS = "tags";
        public static final String CATEGORIES = "categories";
        public static final String FILE_EXTENSION = "extension";
    }

    /**
     * Factory method for creating a simple condition
     */
    public static RuleCondition simple(String field, String operator, Object value) {
        return RuleCondition.builder()
            .type(ConditionType.SIMPLE)
            .field(field)
            .operator(operator)
            .value(value)
            .build();
    }

    /**
     * Factory method for creating an AND condition
     */
    public static RuleCondition and(List<RuleCondition> children) {
        return RuleCondition.builder()
            .type(ConditionType.AND)
            .children(children)
            .build();
    }

    /**
     * Factory method for creating an OR condition
     */
    public static RuleCondition or(List<RuleCondition> children) {
        return RuleCondition.builder()
            .type(ConditionType.OR)
            .children(children)
            .build();
    }

    /**
     * Factory method for creating a NOT condition
     */
    public static RuleCondition not(RuleCondition child) {
        return RuleCondition.builder()
            .type(ConditionType.NOT)
            .children(List.of(child))
            .build();
    }

    /**
     * Factory method for creating an always-true condition
     */
    public static RuleCondition alwaysTrue() {
        return RuleCondition.builder()
            .type(ConditionType.ALWAYS_TRUE)
            .build();
    }
}
