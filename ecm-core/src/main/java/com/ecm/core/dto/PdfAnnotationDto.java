package com.ecm.core.dto;

public record PdfAnnotationDto(
    String id,
    int page,
    double x,
    double y,
    String text,
    String color,
    String createdBy,
    String createdAt
) {}
