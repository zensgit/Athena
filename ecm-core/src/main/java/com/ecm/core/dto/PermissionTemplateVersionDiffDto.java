package com.ecm.core.dto;

import com.ecm.core.entity.Permission;
import com.ecm.core.entity.PermissionSet;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PermissionTemplateVersionDiffDto {

    UUID templateId;
    String templateName;

    UUID fromVersionId;
    Integer fromVersionNumber;

    UUID toVersionId;
    Integer toVersionNumber;

    List<EntryDto> added;
    List<EntryDto> removed;
    List<ChangeDto> changed;

    @Value
    @Builder
    public static class EntryDto {
        String authority;
        Permission.AuthorityType authorityType;
        PermissionSet permissionSet;
    }

    @Value
    @Builder
    public static class ChangeDto {
        EntryDto before;
        EntryDto after;
    }
}

