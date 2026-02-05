package com.ecm.core.dto;

import com.ecm.core.entity.PermissionTemplate;
import com.ecm.core.entity.PermissionTemplateVersion;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PermissionTemplateVersionDetailDto {

    UUID id;
    UUID templateId;
    Integer versionNumber;
    String name;
    String description;
    List<PermissionTemplate.PermissionTemplateEntry> entries;
    String createdBy;
    LocalDateTime createdDate;

    public static PermissionTemplateVersionDetailDto from(PermissionTemplateVersion version) {
        return PermissionTemplateVersionDetailDto.builder()
            .id(version.getId())
            .templateId(version.getTemplateId())
            .versionNumber(version.getVersionNumber())
            .name(version.getName())
            .description(version.getDescription())
            .entries(version.getEntries())
            .createdBy(version.getCreatedBy())
            .createdDate(version.getCreatedDate())
            .build();
    }
}
