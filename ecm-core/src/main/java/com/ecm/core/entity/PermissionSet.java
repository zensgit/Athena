package com.ecm.core.entity;

import java.util.EnumSet;
import java.util.Set;

import com.ecm.core.entity.Permission.PermissionType;

public enum PermissionSet {
    COORDINATOR(EnumSet.allOf(PermissionType.class)),
    EDITOR(EnumSet.of(
        PermissionType.READ,
        PermissionType.WRITE,
        PermissionType.CHECKOUT,
        PermissionType.CHECKIN,
        PermissionType.CANCEL_CHECKOUT
    )),
    CONTRIBUTOR(EnumSet.of(
        PermissionType.READ,
        PermissionType.CREATE_CHILDREN,
        PermissionType.CHECKOUT,
        PermissionType.CHECKIN,
        PermissionType.CANCEL_CHECKOUT
    )),
    CONSUMER(EnumSet.of(PermissionType.READ));

    private final Set<PermissionType> permissions;

    PermissionSet(Set<PermissionType> permissions) {
        this.permissions = permissions;
    }

    public Set<PermissionType> getPermissions() {
        return permissions;
    }
}
