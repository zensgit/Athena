package com.ecm.core.dto;

import java.util.UUID;

public record CheckoutLineageDto(
    UUID documentId,
    CheckoutInfoDto checkout,
    VersionDto baselineVersion,
    VersionDto currentVersion
) {
}
