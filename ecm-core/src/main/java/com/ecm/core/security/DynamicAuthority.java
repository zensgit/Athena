package com.ecm.core.security;

import com.ecm.core.entity.Permission.PermissionType;

import java.util.Set;

/**
 * Dynamic Authority Interface - Inspired by Alfresco DynamicAuthority
 *
 * Runtime-evaluated permissions based on context rather than static ACL records.
 * This allows for dynamic permission rules like:
 * - Document owner has all permissions
 * - Lock owner can unlock
 * - Same department users can read
 */
public interface DynamicAuthority {

    /**
     * Check if user has this dynamic authority in the given context.
     *
     * @param context Permission evaluation context
     * @return true if user has the authority
     */
    boolean hasAuthority(PermissionContext context);

    /**
     * Get the authority identifier.
     *
     * @return Authority name (e.g., "ROLE_OWNER", "ROLE_LOCK_OWNER")
     */
    String getAuthorityName();

    /**
     * Get which permission types this dynamic authority applies to.
     *
     * @return Set of applicable permission types, or null for all permissions
     */
    Set<PermissionType> getApplicablePermissions();

    /**
     * Get the priority of this dynamic authority.
     * Lower values mean higher priority.
     *
     * @return Priority value (default: 100)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Check if this authority grants the permission.
     * Returns null if this authority doesn't make a decision.
     *
     * @return true to grant, false to deny, null for no decision
     */
    default Boolean grantPermission(PermissionContext context) {
        if (hasAuthority(context)) {
            Set<PermissionType> applicable = getApplicablePermissions();
            if (applicable == null || applicable.contains(context.getRequestedPermission())) {
                return true;
            }
        }
        return null; // No decision
    }
}
