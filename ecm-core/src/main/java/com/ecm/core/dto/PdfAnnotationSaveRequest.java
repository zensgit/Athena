package com.ecm.core.dto;

import java.util.List;

public record PdfAnnotationSaveRequest(List<PdfAnnotationDto> annotations) {}
