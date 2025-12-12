package com.ecm.core.pipeline.processor;

import com.ecm.core.entity.Correspondent;
import com.ecm.core.entity.Document;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.CorrespondentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto Matching Processor
 * 
 * Analyzes document content (text) to automatically assign:
 * - Correspondent (Sender/Receiver)
 * 
 * Execution Order: 450 (After persistence, before indexing)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoMatchingProcessor implements DocumentProcessor {

    private final CorrespondentService correspondentService;
    private final NodeRepository nodeRepository;

    @Override
    @Transactional
    public ProcessingResult process(DocumentContext context) {
        Document document = context.getDocument();
        if (document == null) {
            return ProcessingResult.skipped("Document not persisted yet");
        }

        String text = context.getExtractedText();
        if (text == null || text.isBlank()) {
            return ProcessingResult.skipped("No text content for matching");
        }

        boolean matched = false;

        // Match Correspondent
        if (document.getCorrespondent() == null) {
            Correspondent match = correspondentService.matchCorrespondent(text);
            if (match != null) {
                document.setCorrespondent(match);
                // Important: Save the relationship. 
                // Since this runs in a transaction and Document is managed (usually), 
                // explicit save ensures it flushes before indexing.
                nodeRepository.save(document);
                
                log.info("Auto-matched correspondent '{}' for document {}", match.getName(), document.getId());
                context.addMetadata("ecm:correspondent", match.getName());
                matched = true;
            }
        }

        return ProcessingResult.success()
            .withData("matched", matched)
            .withData("correspondent", document.getCorrespondent() != null ? document.getCorrespondent().getName() : null);
    }

    @Override
    public int getOrder() {
        return 450;
    }

    @Override
    public String getName() {
        return "AutoMatchingProcessor";
    }
}
