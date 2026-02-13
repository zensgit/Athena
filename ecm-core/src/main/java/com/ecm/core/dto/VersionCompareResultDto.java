package com.ecm.core.dto;

/**
 * Version comparison result between two specific versions of the same document.
 */
public record VersionCompareResultDto(
    VersionDto from,
    VersionDto to,
    boolean metadataChanged,
    boolean contentChanged,
    Long sizeDifference,
    TextDiffDto textDiff
) {}

