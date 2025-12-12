package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.ml.MLServiceClient;
import com.ecm.core.ml.MLServiceClient.*;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for ML Service integration
 *
 * Provides endpoints for:
 * - Document classification
 * - Tag suggestions
 * - Model training
 * - Service health status
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ml")
@RequiredArgsConstructor
@Tag(name = "ML Service", description = "Machine Learning integration APIs")
public class MLController {

    private final MLServiceClient mlServiceClient;
    private final NodeRepository nodeRepository;

    // ==================== Health Check ====================

    @GetMapping("/health")
    @Operation(summary = "Check ML service health",
               description = "Check if the ML service is available and model is loaded")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        boolean available = mlServiceClient.isAvailable();
        ModelInfo modelInfo = mlServiceClient.getModelInfo();

        return ResponseEntity.ok(Map.of(
            "available", available,
            "modelLoaded", modelInfo.isModelLoaded(),
            "modelVersion", modelInfo.getModelVersion() != null ? modelInfo.getModelVersion() : "N/A",
            "status", available ? "healthy" : "unavailable"
        ));
    }

    // ==================== Classification ====================

    @PostMapping("/classify")
    @Operation(summary = "Classify text",
               description = "Classify text content and get category suggestions")
    public ResponseEntity<ClassificationResult> classifyText(@RequestBody ClassifyTextRequest request) {
        if (request.getText() == null || request.getText().length() < 50) {
            return ResponseEntity.badRequest().body(
                ClassificationResult.failed("Text must be at least 50 characters"));
        }

        ClassificationResult result;
        if (request.getCandidates() != null && !request.getCandidates().isEmpty()) {
            result = mlServiceClient.classifyWithCandidates(request.getText(), request.getCandidates());
        } else {
            result = mlServiceClient.classify(request.getText());
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/classify/{documentId}")
    @Operation(summary = "Classify document",
               description = "Classify a document by its ID using extracted text")
    public ResponseEntity<ClassificationResult> classifyDocument(@PathVariable UUID documentId) {
        Document document = findDocument(documentId);

        String text = extractDocumentText(document);
        if (text == null || text.length() < 50) {
            return ResponseEntity.ok(
                ClassificationResult.failed("Document has insufficient text content"));
        }

        ClassificationResult result = mlServiceClient.classify(text);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/classify-batch")
    @Operation(summary = "Classify multiple documents",
               description = "Classify multiple documents and get category suggestions")
    public ResponseEntity<List<DocumentClassificationResult>> classifyBatch(
            @RequestBody List<UUID> documentIds) {

        List<DocumentClassificationResult> results = documentIds.stream()
            .map(id -> {
                try {
                    Document doc = findDocument(id);
                    String text = extractDocumentText(doc);

                    if (text == null || text.length() < 50) {
                        return new DocumentClassificationResult(
                            id, doc.getName(), false, null, null, "Insufficient text");
                    }

                    ClassificationResult cr = mlServiceClient.classify(text);
                    return new DocumentClassificationResult(
                        id, doc.getName(),
                        cr.isSuccess(),
                        cr.getSuggestedCategory(),
                        cr.getConfidence(),
                        cr.getErrorMessage()
                    );
                } catch (Exception e) {
                    return new DocumentClassificationResult(
                        id, null, false, null, null, e.getMessage());
                }
            })
            .toList();

        return ResponseEntity.ok(results);
    }

    // ==================== Tag Suggestions ====================

    @PostMapping("/suggest-tags")
    @Operation(summary = "Suggest tags",
               description = "Get tag suggestions based on text content")
    public ResponseEntity<List<String>> suggestTags(@RequestBody SuggestTagsRequest request) {
        List<String> tags = mlServiceClient.suggestTags(
            request.getText(),
            request.getMaxTags() != null ? request.getMaxTags() : 5
        );
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/suggest-tags/{documentId}")
    @Operation(summary = "Suggest tags for document",
               description = "Get tag suggestions for a specific document")
    public ResponseEntity<List<String>> suggestTagsForDocument(
            @PathVariable UUID documentId,
            @Parameter(description = "Maximum number of tags")
            @RequestParam(defaultValue = "5") int maxTags) {

        Document document = findDocument(documentId);
        String text = extractDocumentText(document);

        if (text == null || text.length() < 50) {
            return ResponseEntity.ok(List.of());
        }

        List<String> tags = mlServiceClient.suggestTags(text, maxTags);
        return ResponseEntity.ok(tags);
    }

    // ==================== Model Training ====================

    @PostMapping("/train")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Train ML model",
               description = "Train the ML model with labeled documents (admin only)")
    public ResponseEntity<TrainingResult> trainModel(@RequestBody TrainModelRequest request) {
        if (request.getDocuments() == null || request.getDocuments().size() < 10) {
            return ResponseEntity.badRequest().body(
                TrainingResult.failed("Need at least 10 training documents"));
        }

        TrainingResult result = mlServiceClient.trainModel(request.getDocuments());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/train/from-documents")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Train from existing documents",
               description = "Train the ML model using existing categorized documents")
    public ResponseEntity<TrainingResult> trainFromDocuments() {
        // Fetch all documents with categories
        List<TrainingDocument> trainingDocs = nodeRepository.findAllWithCategories()
            .stream()
            .filter(node -> node instanceof Document)
            .map(node -> (Document) node)
            .filter(doc -> !doc.getCategories().isEmpty())
            .filter(doc -> extractDocumentText(doc) != null && extractDocumentText(doc).length() >= 50)
            .map(doc -> TrainingDocument.builder()
                .text(extractDocumentText(doc))
                .category(doc.getCategories().iterator().next().getName())
                .tags(doc.getTags().stream()
                    .map(t -> t.getName())
                    .toList())
                .build())
            .toList();

        if (trainingDocs.size() < 10) {
            return ResponseEntity.badRequest().body(
                TrainingResult.failed("Not enough categorized documents for training. Found: " + trainingDocs.size()));
        }

        log.info("Training ML model with {} documents", trainingDocs.size());
        TrainingResult result = mlServiceClient.trainModel(trainingDocs);
        return ResponseEntity.ok(result);
    }

    // ==================== Helper Methods ====================

    private Document findDocument(UUID documentId) {
        return (Document) nodeRepository.findById(documentId)
            .filter(node -> node instanceof Document)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private String extractDocumentText(Document document) {
        return document.getTextContent();
    }

    // ==================== Request/Response DTOs ====================

    public record ClassifyTextRequest(
        String text,
        List<String> candidates
    ) {
        public String getText() { return text; }
        public List<String> getCandidates() { return candidates; }
    }

    public record SuggestTagsRequest(
        String text,
        Integer maxTags
    ) {
        public String getText() { return text; }
        public Integer getMaxTags() { return maxTags; }
    }

    public record TrainModelRequest(
        List<TrainingDocument> documents
    ) {
        public List<TrainingDocument> getDocuments() { return documents; }
    }

    public record DocumentClassificationResult(
        UUID documentId,
        String documentName,
        boolean success,
        String suggestedCategory,
        Double confidence,
        String error
    ) {}
}
