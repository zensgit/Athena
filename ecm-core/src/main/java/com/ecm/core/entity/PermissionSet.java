package com.ecm.core.entity;

import java.util.EnumSet;
import java.util.Set;

import com.ecm.core.entity.Permission.PermissionType;

public enum PermissionSet {
    COORDINATOR("Coordinator", "Full control including permission changes", 1, EnumSet.allOf(PermissionType.class)),
    EDITOR("Editor", "Edit content and manage versions", 2, EnumSet.of(
        PermissionType.READ,
        PermissionType.WRITE,
        PermissionType.CHECKOUT,
        PermissionType.CHECKIN,
        PermissionType.CANCEL_CHECKOUT
    )),
    CONTRIBUTOR("Contributor", "Create content and add versions", 3, EnumSet.of(
        PermissionType.READ,
        PermissionType.CREATE_CHILDREN,
        PermissionType.CHECKOUT,
        PermissionType.CHECKIN,
        PermissionType.CANCEL_CHECKOUT
    )),
    CONSUMER("Consumer", "Read-only access", 4, EnumSet.of(PermissionType.READ));

    private final String label;
    private final String description;
    private final int order;
    private final Set<PermissionType> permissions;

    PermissionSet(String label, String description, int order, Set<PermissionType> permissions) {
        this.label = label;
        this.description = description;
        this.order = order;
        this.permissions = permissions;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public int getOrder() {
        return order;
    }

    public Set<PermissionType> getPermissions() {
        return permissions;
    }
}
