package com.ecm.core.dto;

import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.PermissionSet;

import java.util.EnumSet;
import java.util.Set;

public record PermissionSetDto(
    String name,
    String label,
    String description,
    int order,
    Set<PermissionType> permissions
) {
    public static PermissionSetDto from(PermissionSet set) {
        if (set == null) {
            return null;
        }
        return new PermissionSetDto(
            set.name(),
            set.getLabel(),
            set.getDescription(),
            set.getOrder(),
            EnumSet.copyOf(set.getPermissions())
        );
    }
}
