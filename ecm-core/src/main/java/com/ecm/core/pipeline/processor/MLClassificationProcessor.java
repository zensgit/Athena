package com.ecm.core.pipeline.processor;

import com.ecm.core.entity.Document;
import com.ecm.core.ml.MLServiceClient;
import com.ecm.core.ml.MLServiceClient.ClassificationResult;
import com.ecm.core.model.Category;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.repository.CategoryRepository;
import com.ecm.core.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * ML Classification Processor
 *
 * Uses the ML Service to classify the document content and suggest categories.
 * If confidence is high enough, it can automatically apply the category.
 *
 * Execution Order: 460 (After persistence, before indexing/events)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MLClassificationProcessor implements DocumentProcessor {

    private final MLServiceClient mlServiceClient;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;

    @Value("${ecm.ml.auto-classify.enabled:true}")
    private boolean autoClassifyEnabled;

    @Value("${ecm.ml.auto-classify.confidence-threshold:0.7}")
    private double confidenceThreshold;

    @Value("${ecm.ml.auto-apply.confidence-threshold:0.85}")
    private double autoApplyThreshold;

    @Override
    @Transactional
    public ProcessingResult process(DocumentContext context) {
        if (!autoClassifyEnabled) {
            return ProcessingResult.skipped("Auto-classify disabled");
        }

        Document document = context.getDocument();
        if (document == null) {
            return ProcessingResult.skipped("Document not persisted yet");
        }
        String text = context.getExtractedText();

        if (text == null || text.length() < 50) {
            return ProcessingResult.skipped("Insufficient text for classification");
        }

        if (!mlServiceClient.isAvailable()) {
            log.debug("ML service unavailable, skipping classification");
            return ProcessingResult.skipped("ML service unavailable");
        }

        try {
            log.debug("Requesting classification for document {}", document.getId());
            ClassificationResult result = mlServiceClient.classify(text);

            if (!result.isSuccess()) {
                return ProcessingResult.success()
                    .withData("classificationSuccess", false)
                    .withData("error", result.getErrorMessage());
            }

            // Store suggestion in context
            context.setSuggestedCategory(result.getSuggestedCategory());
            
            ProcessingResult processingResult = ProcessingResult.success()
                .withData("classificationSuccess", true)
                .withData("category", result.getSuggestedCategory())
                .withData("confidence", result.getConfidence());

            // Auto-apply if confidence is high enough
            if (result.getConfidence() != null && result.getConfidence() >= autoApplyThreshold) {
                applyCategory(document, result.getSuggestedCategory());
                processingResult.withData("autoApplied", true);
                log.info("Auto-applied category '{}' to document {} (confidence: {})",
                    result.getSuggestedCategory(), document.getId(), result.getConfidence());
            } else {
                processingResult.withData("autoApplied", false);
                log.debug("Suggested category '{}' for document {} (confidence: {})",
                    result.getSuggestedCategory(), document.getId(), result.getConfidence());
            }

            return processingResult;

        } catch (Exception e) {
            log.error("ML classification failed for document {}", document.getId(), e);
            // Don't fail the pipeline for ML errors
            return ProcessingResult.success()
                .withData("classificationSuccess", false)
                .withData("error", e.getMessage());
        }
    }

    private void applyCategory(Document document, String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return;

        // Check if document already has this category
        boolean alreadyHas = document.getCategories().stream()
            .anyMatch(c -> c.getName().equalsIgnoreCase(categoryName));
        
        if (alreadyHas) return;

        // Find or create category
        Optional<Category> existing = categoryRepository.findByName(categoryName);
        Category category;
        if (existing.isPresent()) {
            category = existing.get();
        } else {
            // Create new root category
            category = categoryService.createCategory(categoryName, null, "Auto-created by ML");
        }

        document.getCategories().add(category);
        // Note: Repository save happens implicitly if managed, or explicitly in service
        // Since we are in a Transactional method, changes to managed entity 'document' should be flushed
    }

    @Override
    public int getOrder() {
        return 460;
    }

    @Override
    public String getName() {
        return "MLClassificationProcessor";
    }

    @Override
    public boolean supports(DocumentContext context) {
        // Only classify if we have text
        return context.getExtractedText() != null && !context.getExtractedText().isEmpty();
    }
}
