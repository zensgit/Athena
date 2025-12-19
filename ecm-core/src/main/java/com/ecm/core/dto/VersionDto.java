package com.ecm.core.dto;

import com.ecm.core.entity.Version;

import java.time.LocalDateTime;
import java.util.UUID;

public record VersionDto(
    UUID id,
    UUID documentId,
    String versionLabel,
    String comment,
    LocalDateTime createdDate,
    String creator,
    long size,
    boolean major
) {
    public static VersionDto from(Version version) {
        if (version == null) {
            return null;
        }

        UUID documentId = version.getDocument() != null ? version.getDocument().getId() : null;
        return new VersionDto(
            version.getId(),
            documentId,
            version.getVersionLabel() != null ? version.getVersionLabel() : version.getVersionString(),
            version.getComment(),
            version.getCreatedDate(),
            version.getCreatedBy(),
            version.getFileSize() != null ? version.getFileSize() : 0L,
            version.isMajorVersionFlag()
        );
    }
}

