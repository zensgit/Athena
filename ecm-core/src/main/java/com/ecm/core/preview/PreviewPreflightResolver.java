package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class PreviewPreflightResolver {

    private final PreviewFailurePolicyRegistry previewFailurePolicyRegistry;
    private final CadRenderEndpointRegistry cadRenderEndpointRegistry;

    @Value("${ecm.preview.preflight.enabled:true}")
    private boolean preflightEnabled;

    @Value("${ecm.preview.preflight.max-source-size-bytes:268435456}")
    private long defaultMaxSourceSizeBytes;

    @Value("${ecm.preview.preflight.max-source-size-bytes-by-route:}")
    private String maxSourceSizeByRoute;

    public PreviewPreflightResolver(
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry,
        CadRenderEndpointRegistry cadRenderEndpointRegistry
    ) {
        this.previewFailurePolicyRegistry = previewFailurePolicyRegistry;
        this.cadRenderEndpointRegistry = cadRenderEndpointRegistry;
    }

    public PreflightDecision evaluateDocument(Document document) {
        if (document == null) {
            return new PreflightDecision(
                null,
                "unknown",
                "DECLINED",
                "MISSING_DOCUMENT",
                "Missing document metadata",
                "default",
                null,
                Math.max(1L, defaultMaxSourceSizeBytes),
                List.of()
            );
        }
        return evaluateCandidate(
            document.getId(),
            document.getName(),
            document.getMimeType(),
            document.getFileSize()
        );
    }

    public PreflightDecision evaluateCandidate(
        UUID documentId,
        String fileName,
        String mimeType,
        Long sourceSizeBytes
    ) {
        String route = resolveRoute(mimeType, fileName);
        String policyProfileKey = previewFailurePolicyRegistry.resolve(mimeType, fileName).key();
        List<String> pipelineChain = resolvePipelineChain(route);
        long maxSourceBytes = resolveMaxSourceSizeBytes(route);

        if (!preflightEnabled) {
            return new PreflightDecision(
                documentId,
                route,
                "BYPASSED",
                null,
                "Preflight disabled",
                policyProfileKey,
                sourceSizeBytes,
                maxSourceBytes,
                pipelineChain
            );
        }

        if ("unsupported".equals(route)) {
            return new PreflightDecision(
                documentId,
                route,
                "DECLINED",
                "MIME_UNSUPPORTED",
                "Preview pipeline not available for this MIME type",
                policyProfileKey,
                sourceSizeBytes,
                maxSourceBytes,
                pipelineChain
            );
        }

        if ("cad".equals(route)) {
            if (!cadRenderEndpointRegistry.isCadPreviewEnabled()) {
                return new PreflightDecision(
                    documentId,
                    route,
                    "DECLINED",
                    "CAD_DISABLED",
                    "CAD preview is disabled by policy",
                    policyProfileKey,
                    sourceSizeBytes,
                    maxSourceBytes,
                    pipelineChain
                );
            }
            if (!cadRenderEndpointRegistry.isConfigured()) {
                return new PreflightDecision(
                    documentId,
                    route,
                    "DECLINED",
                    "CAD_ENDPOINT_UNCONFIGURED",
                    "CAD renderer endpoint is not configured",
                    policyProfileKey,
                    sourceSizeBytes,
                    maxSourceBytes,
                    pipelineChain
                );
            }
        }

        if (sourceSizeBytes != null && sourceSizeBytes > maxSourceBytes) {
            return new PreflightDecision(
                documentId,
                route,
                "DECLINED",
                "SOURCE_TOO_LARGE",
                "Source size exceeds preflight threshold",
                policyProfileKey,
                sourceSizeBytes,
                maxSourceBytes,
                pipelineChain
            );
        }

        return new PreflightDecision(
            documentId,
            route,
            "ACCEPTED",
            null,
            "Eligible for preview queue",
            policyProfileKey,
            sourceSizeBytes,
            maxSourceBytes,
            pipelineChain
        );
    }

    private String resolveRoute(String mimeType, String fileName) {
        String normalizedMime = normalizeMimeType(mimeType);
        String normalizedName = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);

        if (normalizedMime.contains("dwg")
            || normalizedMime.contains("dxf")
            || normalizedMime.contains("autocad")
            || normalizedMime.contains("cad")
            || normalizedName.endsWith(".dwg")
            || normalizedName.endsWith(".dxf")) {
            return "cad";
        }
        if ("application/pdf".equals(normalizedMime) || normalizedName.endsWith(".pdf")) {
            return "pdf";
        }
        if (normalizedMime.startsWith("image/")) {
            return "image";
        }
        if (normalizedMime.startsWith("text/")) {
            return "text";
        }
        if (normalizedMime.contains("officedocument")
            || normalizedMime.contains("msword")
            || normalizedMime.contains("ms-excel")
            || normalizedMime.contains("ms-powerpoint")
            || normalizedMime.contains("opendocument")
            || normalizedName.endsWith(".doc")
            || normalizedName.endsWith(".docx")
            || normalizedName.endsWith(".xls")
            || normalizedName.endsWith(".xlsx")
            || normalizedName.endsWith(".ppt")
            || normalizedName.endsWith(".pptx")
            || normalizedName.endsWith(".odt")
            || normalizedName.endsWith(".ods")
            || normalizedName.endsWith(".odp")
            || normalizedName.endsWith(".rtf")) {
            return "office";
        }
        return "unsupported";
    }

    private List<String> resolvePipelineChain(String route) {
        if ("cad".equals(route)) {
            List<String> chain = new ArrayList<>();
            for (String endpoint : cadRenderEndpointRegistry.resolveEndpoints()) {
                chain.add("cad-remote:" + endpoint);
            }
            if (chain.isEmpty()) {
                return List.of("cad-remote:unconfigured");
            }
            return chain;
        }
        return switch (route) {
            case "pdf" -> List.of("local-pdfbox");
            case "image" -> List.of("local-thumbnailator");
            case "office" -> List.of("local-poi");
            case "text" -> List.of("local-text");
            default -> List.of();
        };
    }

    private long resolveMaxSourceSizeBytes(String route) {
        long fallback = Math.max(1L, defaultMaxSourceSizeBytes);
        Map<String, Long> overrides = parseRouteSizeOverrides(maxSourceSizeByRoute);
        Long override = overrides.get(route);
        if (override == null || override <= 0L) {
            return fallback;
        }
        return override;
    }

    private static Map<String, Long> parseRouteSizeOverrides(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, Long> map = new LinkedHashMap<>();
        for (String token : raw.split("[,;\\n]")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String[] parts = token.trim().split("[:=]", 2);
            if (parts.length != 2) {
                continue;
            }
            String route = parts[0] != null ? parts[0].trim().toLowerCase(Locale.ROOT) : "";
            if (route.isEmpty()) {
                continue;
            }
            try {
                long value = Long.parseLong(parts[1].trim());
                if (value > 0L) {
                    map.put(route, value);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed override values.
            }
        }
        return map;
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return normalized;
    }

    public record PreflightDecision(
        UUID documentId,
        String route,
        String preflightStatus,
        String skipReason,
        String message,
        String policyProfileKey,
        Long sourceSizeBytes,
        long maxSourceSizeBytes,
        List<String> pipelineChain
    ) {
        public boolean accepted() {
            return "ACCEPTED".equals(preflightStatus) || "BYPASSED".equals(preflightStatus);
        }

        public String pipelineChainSummary() {
            if (pipelineChain == null || pipelineChain.isEmpty()) {
                return "";
            }
            LinkedHashSet<String> deduplicated = new LinkedHashSet<>(pipelineChain);
            return String.join(" > ", deduplicated);
        }
    }
}
