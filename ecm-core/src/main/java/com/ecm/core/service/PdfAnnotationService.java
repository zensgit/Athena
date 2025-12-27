package com.ecm.core.service;

import com.ecm.core.dto.PdfAnnotationDto;
import com.ecm.core.dto.PdfAnnotationSaveRequest;
import com.ecm.core.dto.PdfAnnotationStateDto;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfAnnotationService {

    private static final String METADATA_KEY = "pdfAnnotations";
    private static final int MAX_ANNOTATIONS = 500;
    private static final int MAX_TEXT_LENGTH = 1000;

    private final NodeService nodeService;
    private final SecurityService securityService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PdfAnnotationStateDto getAnnotations(UUID documentId) {
        Document document = requireDocument(documentId);
        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null) {
            return new PdfAnnotationStateDto(List.of(), null, null);
        }
        Object raw = metadata.get(METADATA_KEY);
        return toState(raw);
    }

    @Transactional
    public PdfAnnotationStateDto saveAnnotations(UUID documentId, PdfAnnotationSaveRequest request) {
        Document document = requireDocument(documentId);
        securityService.checkPermission(document, PermissionType.WRITE);

        List<PdfAnnotationDto> incoming = request != null ? request.annotations() : List.of();
        if (incoming == null) {
            incoming = List.of();
        }
        if (incoming.size() > MAX_ANNOTATIONS) {
            throw new IllegalArgumentException("Too many annotations (max " + MAX_ANNOTATIONS + ")");
        }

        String user = securityService.getCurrentUser();
        String now = Instant.now().toString();

        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (PdfAnnotationDto annotation : incoming) {
            if (annotation == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            String id = annotation.id() != null && !annotation.id().isBlank()
                ? annotation.id()
                : UUID.randomUUID().toString();
            entry.put("id", id);
            entry.put("page", Math.max(annotation.page(), 1));
            entry.put("x", clamp(annotation.x(), 0d, 1d));
            entry.put("y", clamp(annotation.y(), 0d, 1d));
            String text = annotation.text() != null ? annotation.text().trim() : "";
            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH);
            }
            entry.put("text", text);
            String color = annotation.color();
            entry.put("color", color != null && !color.isBlank() ? color : "#1976d2");
            entry.put("createdBy", annotation.createdBy() != null ? annotation.createdBy() : user);
            entry.put("createdAt", annotation.createdAt() != null ? annotation.createdAt() : now);
            sanitized.add(entry);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("annotations", sanitized);
        payload.put("updatedBy", user);
        payload.put("updatedAt", now);

        nodeService.updateNode(documentId, Map.of("metadata", Map.of(METADATA_KEY, payload)));
        auditService.logEvent(
            "PDF_ANNOTATIONS_UPDATED",
            document.getId(),
            document.getName(),
            user,
            "Updated PDF annotations (" + sanitized.size() + " items)"
        );

        return toState(payload);
    }

    private Document requireDocument(UUID documentId) {
        Node node = nodeService.getNode(documentId);
        if (node instanceof Document document) {
            return document;
        }
        throw new IllegalArgumentException("Node is not a document: " + documentId);
    }

    private PdfAnnotationStateDto toState(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return new PdfAnnotationStateDto(List.of(), null, null);
        }
        Object annotationsRaw = rawMap.get("annotations");
        List<PdfAnnotationDto> annotations = new ArrayList<>();
        if (annotationsRaw instanceof List<?> list) {
            for (Object entry : list) {
                PdfAnnotationDto dto = toAnnotation(entry);
                if (dto != null) {
                    annotations.add(dto);
                }
            }
        }
        String updatedBy = asString(rawMap.get("updatedBy"));
        String updatedAt = asString(rawMap.get("updatedAt"));
        return new PdfAnnotationStateDto(annotations, updatedBy, updatedAt);
    }

    private PdfAnnotationDto toAnnotation(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        return new PdfAnnotationDto(
            asString(map.get("id")),
            asInt(map.get("page"), 1),
            asDouble(map.get("x"), 0d),
            asDouble(map.get("y"), 0d),
            asString(map.get("text")),
            asString(map.get("color")),
            asString(map.get("createdBy")),
            asString(map.get("createdAt"))
        );
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.min(Math.max(value, min), max);
    }
}
