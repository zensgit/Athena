package com.ecm.core.asynctask;

import java.time.Instant;
import java.util.List;

public record AsyncTaskLifecycleListSnapshot(
    Instant generatedAt,
    String domainFilter,
    String statusFilter,
    int count,
    long totalCount,
    int skipCount,
    int maxItems,
    boolean hasMoreItems,
    List<AsyncTaskStatusSnapshot> items
) {}
