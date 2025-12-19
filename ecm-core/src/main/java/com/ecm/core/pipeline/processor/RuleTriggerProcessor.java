package com.ecm.core.pipeline.processor;

import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.RuleExecutionResult;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rule Trigger Processor
 *
 * Triggers automation rules for newly uploaded documents (DOCUMENT_CREATED event).
 * This processor runs after document persistence but before search indexing.
 *
 * Execution Order: 470 (After MLClassificationProcessor(460), before SearchIndexProcessor(500))
 *
 * Key Design Decisions:
 * - Runs in request thread (has SecurityContext) to ensure rule actions can write to DB
 * - Catches all exceptions to avoid failing the upload pipeline
 * - Records rule execution results in context for debugging
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleTriggerProcessor implements DocumentProcessor {

    private final RuleEngineService ruleEngineService;

    @Value("${ecm.rules.enabled:true}")
    private boolean rulesEnabled;

    @Override
    public ProcessingResult process(DocumentContext context) {
        if (!rulesEnabled) {
            return ProcessingResult.skipped("Rule engine disabled");
        }

        Document document = context.getDocument();
        if (document == null) {
            return ProcessingResult.skipped("Document not persisted yet");
        }

        try {
            log.debug("Triggering rules for newly created document: {} ({})",
                document.getName(), document.getId());

            List<RuleExecutionResult> results = ruleEngineService.evaluateAndExecute(
                document, TriggerType.DOCUMENT_CREATED);

            int matchedCount = (int) results.stream()
                .filter(RuleExecutionResult::isConditionMatched)
                .count();
            int successCount = (int) results.stream()
                .filter(r -> r.isConditionMatched() && r.isSuccess())
                .count();

            if (matchedCount > 0) {
                log.info("Rule execution for document {}: {} rules matched, {} succeeded",
                    document.getId(), matchedCount, successCount);
            } else {
                log.debug("No rules matched for document {}", document.getId());
            }

            return ProcessingResult.success()
                .withData("rulesEvaluated", results.size())
                .withData("rulesMatched", matchedCount)
                .withData("rulesSucceeded", successCount);

        } catch (Exception e) {
            // Log error but don't fail the upload pipeline
            log.error("Rule evaluation failed for document {}: {}",
                document.getId(), e.getMessage(), e);

            // Record error in context for debugging
            context.addError(getName(), "Rule evaluation failed: " + e.getMessage());

            // Return success to allow pipeline to continue
            return ProcessingResult.success()
                .withData("ruleError", e.getMessage())
                .withData("rulesEvaluated", 0);
        }
    }

    @Override
    public int getOrder() {
        return 470;
    }

    @Override
    public String getName() {
        return "RuleTriggerProcessor";
    }

    @Override
    public boolean supports(DocumentContext context) {
        // Only trigger rules for documents (not folders)
        return context.getDocument() != null;
    }
}
