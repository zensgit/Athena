package com.ecm.core.search;

import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Lightweight pageable DTO to avoid direct deserialization of Pageable.
 */
@Data
public class SimplePageRequest {
    private int page = 0;
    private int size = 20;

    public Pageable toPageable() {
        int pageNumber = page < 0 ? 0 : page;
        int pageSize = size <= 0 ? 20 : size;
        return PageRequest.of(pageNumber, pageSize);
    }
}
