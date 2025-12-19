package com.ecm.core.dto;

import com.ecm.core.entity.Permission;

import java.time.LocalDateTime;
import java.util.UUID;

public record PermissionDto(
    UUID id,
    String authority,
    String authorityType,
    String permission,
    boolean allowed,
    boolean inherited,
    LocalDateTime expiryDate,
    String notes
) {
    public static PermissionDto from(Permission permission) {
        if (permission == null) {
            return null;
        }
        return new PermissionDto(
            permission.getId(),
            permission.getAuthority(),
            permission.getAuthorityType() != null ? permission.getAuthorityType().name() : null,
            permission.getPermission() != null ? permission.getPermission().name() : null,
            permission.isAllowed(),
            permission.isInherited(),
            permission.getExpiryDate(),
            permission.getNotes()
        );
    }
}

