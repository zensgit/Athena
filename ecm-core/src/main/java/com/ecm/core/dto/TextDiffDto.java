package com.ecm.core.dto;

/**
 * Lightweight line-based diff response for version comparison.
 *
 * <p>We intentionally keep this payload small and safe: it is truncated by the server
 * using max-bytes/max-lines/max-chars limits.</p>
 */
public record TextDiffDto(
    boolean available,
    boolean truncated,
    String reason,
    String diff
) {}

