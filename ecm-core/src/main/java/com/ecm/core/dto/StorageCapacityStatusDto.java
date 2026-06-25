package com.ecm.core.dto;

public record StorageCapacityStatusDto(
    String backendType,
    String status,
    long totalBytes,
    long usableBytes,
    long usedBytes,
    double usedPercent,
    int warnPercent,
    int criticalPercent,
    long blockedMinFreeBytes,
    String rootPath,
    String error
) {
}
