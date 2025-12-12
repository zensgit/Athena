package com.ecm.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Rule Action
 *
 * Represents an action to be executed when a rule's conditions are met.
 * Actions have a type and parameters specific to that type.
 *
 * Examples:
 *
 * Add tag action:
 * {
 *   "type": "ADD_TAG",
 *   "params": {"tagName": "important"}
 * }
 *
 * Move to folder action:
 * {
 *   "type": "MOVE_TO_FOLDER",
 *   "params": {"folderId": "uuid-here"}
 * }
 *
 * Set category action:
 * {
 *   "type": "SET_CATEGORY",
 *   "params": {"categoryName": "Finance"}
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Type of action to execute
     */
    private ActionType type;

    /**
     * Parameters for the action
     */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /**
     * Whether to continue if this action fails
     * Default: true (continue processing)
     */
    @Builder.Default
    private Boolean continueOnError = true;

    /**
     * Order of execution when multiple actions exist
     * Default: 0
     */
    @Builder.Default
    private Integer order = 0;

    /**
     * Types of actions that can be executed
     */
    public enum ActionType {
        /**
         * Add a tag to the document
         * Params: tagName (String)
         */
        ADD_TAG,

        /**
         * Remove a tag from the document
         * Params: tagName (String)
         */
        REMOVE_TAG,

        /**
         * Set document category
         * Params: categoryName (String)
         */
        SET_CATEGORY,

        /**
         * Remove a category from the document
         * Params: categoryName (String)
         */
        REMOVE_CATEGORY,

        /**
         * Move document to a different folder
         * Params: folderId (UUID as String)
         */
        MOVE_TO_FOLDER,

        /**
         * Copy document to another folder
         * Params: folderId (UUID as String), newName (String, optional)
         */
        COPY_TO_FOLDER,

        /**
         * Set metadata field
         * Params: key (String), value (Object)
         */
        SET_METADATA,

        /**
         * Remove metadata field
         * Params: key (String)
         */
        REMOVE_METADATA,

        /**
         * Rename the document
         * Params: newName (String), pattern (String, optional for pattern-based rename)
         */
        RENAME,

        /**
         * Start a workflow
         * Params: workflowKey (String), variables (Map, optional)
         */
        START_WORKFLOW,

        /**
         * Send notification
         * Params: recipient (String), message (String), type (email/webhook/internal)
         */
        SEND_NOTIFICATION,

        /**
         * Send webhook request
         * Params: url (String), method (GET/POST), headers (Map), body (String)
         */
        WEBHOOK,

        /**
         * Update document status
         * Params: status (String: ACTIVE/ARCHIVED)
         */
        SET_STATUS,

        /**
         * Lock the document
         * Params: none
         */
        LOCK_DOCUMENT,

        /**
         * Execute custom script (future)
         * Params: script (String), language (String)
         */
        EXECUTE_SCRIPT
    }

    /**
     * Parameter keys for different action types
     */
    public static class ParamKeys {
        // Tag/Category actions
        public static final String TAG_NAME = "tagName";
        public static final String CATEGORY_NAME = "categoryName";

        // Folder actions
        public static final String FOLDER_ID = "folderId";
        public static final String NEW_NAME = "newName";

        // Metadata actions
        public static final String KEY = "key";
        public static final String VALUE = "value";

        // Rename action
        public static final String PATTERN = "pattern";

        // Workflow action
        public static final String WORKFLOW_KEY = "workflowKey";
        public static final String VARIABLES = "variables";

        // Notification action
        public static final String RECIPIENT = "recipient";
        public static final String MESSAGE = "message";
        public static final String NOTIFICATION_TYPE = "type";

        // Webhook action
        public static final String URL = "url";
        public static final String METHOD = "method";
        public static final String HEADERS = "headers";
        public static final String BODY = "body";

        // Status action
        public static final String STATUS = "status";
    }

    /**
     * Get a parameter value
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key) {
        return (T) params.get(key);
    }

    /**
     * Get a parameter value with default
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, T defaultValue) {
        Object value = params.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Factory method for ADD_TAG action
     */
    public static RuleAction addTag(String tagName) {
        return RuleAction.builder()
            .type(ActionType.ADD_TAG)
            .params(Map.of(ParamKeys.TAG_NAME, tagName))
            .build();
    }

    /**
     * Factory method for SET_CATEGORY action
     */
    public static RuleAction setCategory(String categoryName) {
        return RuleAction.builder()
            .type(ActionType.SET_CATEGORY)
            .params(Map.of(ParamKeys.CATEGORY_NAME, categoryName))
            .build();
    }

    /**
     * Factory method for MOVE_TO_FOLDER action
     */
    public static RuleAction moveToFolder(String folderId) {
        return RuleAction.builder()
            .type(ActionType.MOVE_TO_FOLDER)
            .params(Map.of(ParamKeys.FOLDER_ID, folderId))
            .build();
    }

    /**
     * Factory method for SEND_NOTIFICATION action
     */
    public static RuleAction sendNotification(String recipient, String message) {
        return RuleAction.builder()
            .type(ActionType.SEND_NOTIFICATION)
            .params(Map.of(
                ParamKeys.RECIPIENT, recipient,
                ParamKeys.MESSAGE, message
            ))
            .build();
    }

    /**
     * Factory method for SET_METADATA action
     */
    public static RuleAction setMetadata(String key, Object value) {
        return RuleAction.builder()
            .type(ActionType.SET_METADATA)
            .params(Map.of(
                ParamKeys.KEY, key,
                ParamKeys.VALUE, value
            ))
            .build();
    }
}
