package com.ecm.core.dto;

import com.ecm.core.entity.PermissionTemplateVersion;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class PermissionTemplateVersionDto {

    UUID id;
    UUID templateId;
    Integer versionNumber;
    String name;
    String description;
    int entryCount;
    String createdBy;
    LocalDateTime createdDate;

    public static PermissionTemplateVersionDto from(PermissionTemplateVersion version) {
        return PermissionTemplateVersionDto.builder()
            .id(version.getId())
            .templateId(version.getTemplateId())
            .versionNumber(version.getVersionNumber())
            .name(version.getName())
            .description(version.getDescription())
            .entryCount(version.getEntries() == null ? 0 : version.getEntries().size())
            .createdBy(version.getCreatedBy())
            .createdDate(version.getCreatedDate())
            .build();
    }
}
