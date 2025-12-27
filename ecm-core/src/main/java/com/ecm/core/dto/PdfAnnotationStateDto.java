package com.ecm.core.dto;

import java.util.List;

public record PdfAnnotationStateDto(
    List<PdfAnnotationDto> annotations,
    String updatedBy,
    String updatedAt
) {}
