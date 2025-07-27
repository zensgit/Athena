package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.integration.odoo.*;
import com.ecm.core.service.NodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/odoo")
@RequiredArgsConstructor
@Tag(name = "Odoo Integration", description = "APIs for Odoo ERP integration")
public class OdooIntegrationController {
    
    private final OdooService odooService;
    private final NodeService nodeService;
    
    @PostMapping("/connect")
    @Operation(summary = "Test Odoo connection", description = "Test connection to Odoo server")
    public ResponseEntity<Map<String, String>> testConnection() {
        try {
            odooService.initializeConnection();
            return ResponseEntity.ok(Map.of("status", "connected", "message", "Successfully connected to Odoo"));
        } catch (OdooException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
    
    @PostMapping("/documents/{documentId}/attach")
    @Operation(summary = "Attach document to Odoo record", description = "Create an attachment in Odoo for a document")
    public ResponseEntity<OdooAttachment> attachDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "Odoo model name") @RequestParam String model,
            @Parameter(description = "Odoo record ID") @RequestParam Integer recordId) {
        
        try {
            Document document = (Document) nodeService.getNode(documentId);
            OdooAttachment attachment = odooService.createAttachment(document, recordId, model);
            return ResponseEntity.status(HttpStatus.CREATED).body(attachment);
        } catch (OdooException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PostMapping("/documents/{documentId}/link")
    @Operation(summary = "Link document to Odoo record", description = "Create a link between document and Odoo record")
    public ResponseEntity<Void> linkDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "Odoo model name") @RequestParam String model,
            @Parameter(description = "Odoo record ID") @RequestParam Integer recordId,
            @Parameter(description = "Link type") @RequestParam(defaultValue = "attachment") String linkType) {
        
        try {
            Document document = (Document) nodeService.getNode(documentId);
            odooService.linkDocumentToRecord(document, model, recordId, linkType);
            return ResponseEntity.ok().build();
        } catch (OdooException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @GetMapping("/attachments")
    @Operation(summary = "Get Odoo attachments", description = "Get attachments for an Odoo record")
    public ResponseEntity<List<OdooAttachment>> getAttachments(
            @Parameter(description = "Odoo model name") @RequestParam String model,
            @Parameter(description = "Odoo record ID") @RequestParam Integer recordId) {
        
        try {
            List<OdooAttachment> attachments = odooService.getAttachments(model, recordId);
            return ResponseEntity.ok(attachments);
        } catch (OdooException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @GetMapping("/models")
    @Operation(summary = "List Odoo models", description = "Get list of available Odoo models")
    public ResponseEntity<List<OdooModel>> getModels() {
        try {
            List<OdooModel> models = odooService.getModels();
            return ResponseEntity.ok(models);
        } catch (OdooException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
    
    @GetMapping("/records")
    @Operation(summary = "Search Odoo records", description = "Search for records in Odoo")
    public ResponseEntity<List<Map<String, Object>>> searchRecords(
            @Parameter(description = "Odoo model name") @RequestParam String model,
            @Parameter(description = "Search domain") @RequestBody(required = false) List<Object> domain,
            @Parameter(description = "Search options") @RequestParam Map<String, Object> options) {
        
        try {
            List<Map<String, Object>> records = odooService.searchRecords(model, domain, options);
            return ResponseEntity.ok(records);
        } catch (OdooException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @GetMapping("/records/{model}/{recordId}")
    @Operation(summary = "Get Odoo record", description = "Get a specific Odoo record")
    public ResponseEntity<Map<String, Object>> getRecord(
            @Parameter(description = "Odoo model name") @PathVariable String model,
            @Parameter(description = "Odoo record ID") @PathVariable Integer recordId,
            @Parameter(description = "Fields to retrieve") @RequestParam(required = false) List<String> fields) {
        
        try {
            Map<String, Object> record = odooService.getRecord(model, recordId, fields);
            return ResponseEntity.ok(record);
        } catch (OdooException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @PostMapping("/documents/{documentId}/sync")
    @Operation(summary = "Sync document metadata", description = "Sync document metadata with Odoo record")
    public ResponseEntity<Void> syncMetadata(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "Odoo model name") @RequestParam String model,
            @Parameter(description = "Odoo record ID") @RequestParam Integer recordId) {
        
        try {
            Document document = (Document) nodeService.getNode(documentId);
            odooService.syncDocumentMetadata(document, model, recordId);
            return ResponseEntity.ok().build();
        } catch (OdooException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PostMapping("/workflow/create-task")
    @Operation(summary = "Create workflow task", description = "Create a workflow task in Odoo")
    public ResponseEntity<Void> createWorkflowTask(
            @Parameter(description = "Node ID") @RequestParam UUID nodeId,
            @Parameter(description = "Workflow type") @RequestParam String workflowType,
            @RequestBody Map<String, Object> context) {
        
        try {
            var node = nodeService.getNode(nodeId);
            odooService.createWorkflowTask(node, workflowType, context);
            return ResponseEntity.ok().build();
        } catch (OdooException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}