package com.ecm.core.controller;

import com.ecm.core.entity.ModelStatus;
import com.ecm.core.repository.ContentModelDefinitionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Repository discovery endpoint — returns version, build metadata,
 * installed modules, and platform capabilities.
 * Modelled after the Alfresco Discovery API ({@code GET /api/-default-/public/alfresco/versions/1/discovery}).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/discovery", "/api/v1/discovery"})
@Tag(name = "Discovery", description = "Repository capability and module discovery")
public class DiscoveryController {

    private final ContentModelDefinitionRepository contentModelRepo;

    @Value("${ecm.version:1.0.0}")
    private String version;

    @Value("${ecm.build.number:dev}")
    private String buildNumber;

    @Value("${ecm.build.date:}")
    private String buildDate;

    @Value("${ecm.edition:Community}")
    private String edition;

    @Value("${spring.application.name:ecm-core}")
    private String applicationName;

    @GetMapping
    @Operation(summary = "Get repository information",
               description = "Returns version, build metadata, installed modules, and capabilities")
    public ResponseEntity<DiscoveryResponse> getDiscovery() {
        List<ModuleInfo> modules = List.of(
            new ModuleInfo("ecm-core", "ECM Core Repository", version),
            new ModuleInfo("ecm-frontend", "ECM Frontend SPA", version),
            new ModuleInfo("ecm-search", "Elasticsearch Integration", version),
            new ModuleInfo("ecm-preview", "Document Preview Pipeline", version),
            new ModuleInfo("ecm-workflow", "Flowable BPM Integration", version),
            new ModuleInfo("ecm-rules", "Automation Rule Engine", version),
            new ModuleInfo("ecm-wopi", "WOPI / Collabora Integration", version),
            new ModuleInfo("ecm-ocr", "Tesseract OCR Pipeline", version),
            new ModuleInfo("ecm-mail", "IMAP/OAuth Mail Ingestion", version),
            new ModuleInfo("ecm-odoo", "Odoo ERP Integration", version),
            new ModuleInfo("ecm-virus", "ClamAV Virus Scanning", version),
            new ModuleInfo("ecm-ml", "ML Classification Service", version)
        );

        long activeModels = contentModelRepo.findByStatus(ModelStatus.ACTIVE).size();

        List<String> capabilities = List.of(
            "versioning",
            "checkout",
            "working-copy",
            "locking",
            "lock-types",
            "permissions",
            "permission-templates",
            "search",
            "faceted-search",
            "full-text-search",
            "preview",
            "renditions",
            "workflow",
            "automation-rules",
            "comments",
            "nested-comments",
            "tags",
            "categories",
            "favorites",
            "share-links",
            "associations",
            "secondary-children",
            "content-models",
            "aspects",
            "property-constraints",
            "audit-log",
            "batch-download",
            "webhooks",
            "webdav",
            "wopi",
            "ocr",
            "virus-scan",
            "ml-classification",
            "barcode-extraction",
            "pdf-annotations",
            "mail-ingestion",
            "erp-integration",
            "multi-language"
        );

        RepositoryInfo repoInfo = new RepositoryInfo(
            applicationName,
            edition,
            new VersionInfo(version, buildNumber, buildDate.isBlank() ? null : buildDate),
            modules,
            capabilities,
            new StatusInfo("RUNNING", Instant.now().toString()),
            Map.of("activeContentModels", activeModels)
        );

        return ResponseEntity.ok(new DiscoveryResponse(repoInfo));
    }

    // ---- response records --------------------------------------------------

    public record DiscoveryResponse(RepositoryInfo repository) {}

    public record RepositoryInfo(
        String id,
        String edition,
        VersionInfo version,
        List<ModuleInfo> modules,
        List<String> capabilities,
        StatusInfo status,
        Map<String, Object> metrics
    ) {}

    public record VersionInfo(String display, String buildNumber, String buildDate) {}

    public record ModuleInfo(String id, String title, String version) {}

    public record StatusInfo(String state, String timestamp) {}
}
