package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.service.NodeService;
import com.ecm.core.integration.odoo.OdooIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integration/odoo")
@RequiredArgsConstructor
@Tag(name = "Integration: Odoo", description = "Odoo ERP integration endpoints")
public class OdooIntegrationController {

    private final OdooIntegrationService odooService;
    private final NodeService nodeService;

    @PostMapping("/link/{documentId}")
    @Operation(summary = "Link to Odoo", description = "Link an ECM document to an Odoo record")
    public ResponseEntity<Map<String, Object>> linkDocumentToOdoo(
            @PathVariable UUID documentId,
            @RequestBody OdooLinkRequest request) {

        Document document = (Document) nodeService.getNode(documentId);
        Integer attachmentId = odooService.exportToOdoo(document, request.model(), request.resourceId());

        return ResponseEntity.ok(Map.of(
            "documentId", documentId,
            "odooAttachmentId", attachmentId,
            "odooModel", request.model(),
            "odooResourceId", request.resourceId()
        ));
    }

    @PostMapping("/tasks/create")
    @Operation(summary = "Create Odoo Task", description = "Create a task in Odoo based on ECM event")
    public ResponseEntity<Map<String, Object>> createOdooTask(@RequestBody CreateTaskRequest request) {
        Integer taskId = odooService.createTask(request.title(), request.description(), request.projectId());
        return ResponseEntity.ok(Map.of("taskId", taskId));
    }

    public record OdooLinkRequest(String model, Integer resourceId) {}
    public record CreateTaskRequest(String title, String description, Integer projectId) {}
}
