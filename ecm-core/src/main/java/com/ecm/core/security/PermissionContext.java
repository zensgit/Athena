package com.ecm.core.security;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Permission Check Context
 *
 * Contains all information needed for permission evaluation:
 * - The node being accessed
 * - The user requesting access
 * - The permission being requested
 * - Additional attributes for complex rules
 */
@Data
@Builder
public class PermissionContext {

    /**
     * Node ID being accessed
     */
    private UUID nodeId;

    /**
     * Node entity (may be null if only ID is known)
     */
    private Node node;

    /**
     * Username of the user requesting access
     */
    private String username;

    /**
     * Permission type being requested
     */
    private PermissionType requestedPermission;

    /**
     * Additional attributes for complex permission rules
     * e.g., IP address, department, time of day
     */
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * Get an attribute value
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Set an attribute value
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Check if context has a specific attribute
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }
}
