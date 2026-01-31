package com.ecm.core.service;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.entity.AutomationRule;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.RuleExecutionResult;
import com.ecm.core.entity.Version;
import com.ecm.core.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;

    @Value("${ecm.audit.disabled-categories:}")
    private String disabledCategoriesRaw;

    private final Set<AuditCategory> disabledCategories = EnumSet.noneOf(AuditCategory.class);

    @PostConstruct
    public void init() {
        if (disabledCategoriesRaw == null || disabledCategoriesRaw.isBlank()) {
            return;
        }
        Arrays.stream(disabledCategoriesRaw.split("[,\\s]+"))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .forEach(value -> {
                try {
                    disabledCategories.add(AuditCategory.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    log.warn("Unknown audit category '{}'", value);
                }
            });
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String eventType, UUID nodeId, String nodeName, String username, String details) {
        try {
            if (!isCategoryEnabled(eventType)) {
                return;
            }
            AuditLog logEntry = AuditLog.builder()
                .eventType(eventType)
                .nodeId(nodeId)
                .nodeName(nodeName)
                .username(username)
                .eventTime(LocalDateTime.now())
                .details(details)
                .build();
            
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }

    private boolean isCategoryEnabled(String eventType) {
        AuditCategory category = resolveCategory(eventType);
        return !disabledCategories.contains(category);
    }

    private AuditCategory resolveCategory(String eventType) {
        if (eventType == null) {
            return AuditCategory.OTHER;
        }
        String upper = eventType.toUpperCase(Locale.ROOT);
        if (upper.startsWith("NODE_")) {
            return AuditCategory.NODE;
        }
        if (upper.startsWith("VERSION_")) {
            return AuditCategory.VERSION;
        }
        if (upper.startsWith("RULE_") || upper.startsWith("SCHEDULED_RULE")) {
            return AuditCategory.RULE;
        }
        if (upper.startsWith("WORKFLOW_") || upper.startsWith("STATUS_")) {
            return AuditCategory.WORKFLOW;
        }
        if (upper.startsWith("MAIL_")) {
            return AuditCategory.MAIL;
        }
        if (upper.startsWith("WOPI_")) {
            return AuditCategory.INTEGRATION;
        }
        if (upper.startsWith("SECURITY_")) {
            return AuditCategory.SECURITY;
        }
        if (upper.startsWith("PDF_")) {
            return AuditCategory.PDF;
        }
        return AuditCategory.OTHER;
    }

    private enum AuditCategory {
        NODE,
        VERSION,
        RULE,
        WORKFLOW,
        MAIL,
        INTEGRATION,
        SECURITY,
        PDF,
        OTHER
    }
    
    public void logNodeCreated(Node node, String username) {
        logEvent("NODE_CREATED", node.getId(), node.getName(), username, 
            String.format("Created %s: %s", node.getNodeType(), node.getName()));
    }
    
    public void logNodeUpdated(Node node, String username) {
        logEvent("NODE_UPDATED", node.getId(), node.getName(), username, 
            String.format("Updated %s: %s", node.getNodeType(), node.getName()));
    }
    
    public void logNodeDeleted(Node node, String username, boolean permanent) {
        logEvent(permanent ? "NODE_DELETED" : "NODE_SOFT_DELETED", 
            node.getId(), node.getName(), username, 
            String.format("%s deleted %s: %s", permanent ? "Permanently" : "Soft", node.getNodeType(), node.getName()));
    }
    
    public void logNodeMoved(Node node, Node oldParent, Node newParent, String username) {
        String oldPath = oldParent != null ? oldParent.getPath() : "/";
        String newPath = newParent != null ? newParent.getPath() : "/";
        logEvent("NODE_MOVED", node.getId(), node.getName(), username, 
            String.format("Moved from %s to %s", oldPath, newPath));
    }
    
    public void logNodeCopied(Node copy, Node source, String username) {
        logEvent("NODE_COPIED", copy.getId(), copy.getName(), username, 
            String.format("Copied from %s (%s)", source.getName(), source.getId()));
    }
    
    public void logNodeLocked(Node node, String username) {
        logEvent("NODE_LOCKED", node.getId(), node.getName(), username, "Locked for editing");
    }
    
    public void logNodeUnlocked(Node node, String username) {
        logEvent("NODE_UNLOCKED", node.getId(), node.getName(), username, "Unlocked");
    }
    
    public void logVersionCreated(Version version, String username) {
        logEvent("VERSION_CREATED", version.getDocument().getId(), version.getDocument().getName(), username, 
            String.format("Created version %s", version.getVersionLabel()));
    }
    
    public void logVersionDeleted(Version version, String username) {
        logEvent("VERSION_DELETED", version.getDocument().getId(), version.getDocument().getName(), username, 
            String.format("Deleted version %s", version.getVersionLabel()));
    }
    
    public void logVersionReverted(Node document, Version targetVersion, String username) {
        logEvent("VERSION_REVERTED", document.getId(), document.getName(), username,
            String.format("Reverted to version %s", targetVersion.getVersionLabel()));
    }

    // ==================== Rule Execution Audit ====================

    /**
     * Log rule execution result (summary level, not per-action)
     * Records rule match/execution outcome to avoid log explosion while maintaining audit trail
     */
    public void logRuleExecution(RuleExecutionResult result, String username) {
        if (result == null || result.getRule() == null) {
            return;
        }

        AutomationRule rule = result.getRule();
        String eventType = result.isSuccess() ? "RULE_EXECUTED" : "RULE_EXECUTION_FAILED";

        String details = String.format(
            "Rule '%s' [%s] on document '%s': %s (actions: %d/%d succeeded, duration: %dms)",
            rule.getName(),
            result.getTriggerType(),
            result.getDocumentName(),
            result.isSuccess() ? "SUCCESS" : "FAILED",
            result.getSuccessfulActionCount(),
            result.getTotalActionCount(),
            result.getDurationMs() != null ? result.getDurationMs() : 0
        );

        if (!result.isSuccess() && result.getErrorMessage() != null) {
            details += " - Error: " + result.getErrorMessage();
        }

        logEvent(eventType, result.getDocumentId(), result.getDocumentName(), username, details);
    }

    /**
     * Log when a rule condition did not match (optional, for debugging/analytics)
     * Only called when explicit tracking is needed, not for every non-match
     */
    public void logRuleNotMatched(AutomationRule rule, UUID documentId, String documentName, String username) {
        logEvent("RULE_NOT_MATCHED", documentId, documentName, username,
            String.format("Rule '%s' condition not satisfied", rule.getName()));
    }

    /**
     * Log scheduled rule batch execution summary
     * Provides high-level overview without logging every document processed
     */
    public void logScheduledRuleBatchExecution(AutomationRule rule, int documentsProcessed,
            int successCount, int failureCount, long durationMs, String username) {
        String eventType = failureCount == 0 ? "SCHEDULED_RULE_BATCH_COMPLETED" : "SCHEDULED_RULE_BATCH_PARTIAL";
        String details = String.format(
            "Scheduled rule '%s' batch execution: %d documents processed (%d succeeded, %d failed) in %dms",
            rule.getName(), documentsProcessed, successCount, failureCount, durationMs
        );

        // Use rule ID as nodeId for batch operations (no single document)
        logEvent(eventType, rule.getId(), rule.getName(), username, details);
    }
}
