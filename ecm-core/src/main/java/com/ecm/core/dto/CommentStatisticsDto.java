package com.ecm.core.dto;

import com.ecm.core.service.CommentService;

import java.util.LinkedHashMap;
import java.util.Map;

public record CommentStatisticsDto(
    String nodeId,
    Long totalComments,
    Long uniqueCommenters,
    Map<String, Long> topCommenters
) {
    public static CommentStatisticsDto from(CommentService.CommentStatistics statistics) {
        if (statistics == null) {
            return null;
        }

        Map<String, Long> topCommenters = statistics.getTopCommenters() == null
            ? Map.of()
            : new LinkedHashMap<>(statistics.getTopCommenters());
        return new CommentStatisticsDto(
            statistics.getNodeId(),
            statistics.getTotalComments(),
            statistics.getUniqueCommenters(),
            topCommenters
        );
    }
}
