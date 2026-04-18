package com.ecm.core.event;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepositoryLifecycleRuleListener {

    private final RuleEngineService ruleEngineService;

    @Value("${ecm.rules.enabled:true}")
    private boolean rulesEnabled;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRepositoryLifecycle(RepositoryLifecycleEvent event) {
        if (!rulesEnabled || event == null) {
            return;
        }

        TriggerType triggerType = event.getRuleTriggerType();
        Document document = resolveDocument(event);
        if (triggerType == null || document == null) {
            return;
        }

        try {
            log.debug("Dispatching lifecycle rule trigger {} for document {}", triggerType, document.getId());
            ruleEngineService.evaluateAndExecute(document, triggerType);
        } catch (Exception e) {
            log.error(
                "Failed to dispatch lifecycle rule trigger {} for document {}: {}",
                triggerType,
                document.getId(),
                e.getMessage(),
                e
            );
        }
    }

    private Document resolveDocument(RepositoryLifecycleEvent event) {
        if (event.getDocument() != null) {
            return event.getDocument();
        }
        if (event.getNode() instanceof Document document) {
            return document;
        }
        return event.getVersion() != null ? event.getVersion().getDocument() : null;
    }
}
