package com.ecm.core.service;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.RuleExecutionResult;
import com.ecm.core.repository.AutomationRuleRepository;
import com.ecm.core.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Scheduled runner for SCHEDULED trigger type rules.
 * Runs periodically to check for rules due for execution and processes them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledRuleRunner {

    private final AutomationRuleRepository ruleRepository;
    private final DocumentRepository documentRepository;
    private final RuleEngineService ruleEngineService;
    private final AuditService auditService;
    private final SecurityService securityService;

    @Value("${ecm.rules.enabled:true}")
    private boolean rulesEnabled;

    @Value("${ecm.rules.scheduled.enabled:true}")
    private boolean scheduledRulesEnabled;

    @Value("${ecm.rules.scheduled.poll-interval-ms:60000}")
    private long pollIntervalMs;

    /**
     * Main scheduler that runs at a fixed delay to check for due scheduled rules.
     * Default: runs every minute (60000ms).
     */
    @Scheduled(fixedDelayString = "${ecm.rules.scheduled.poll-interval-ms:60000}")
    @Transactional
    public void runScheduledRules() {
        if (!rulesEnabled || !scheduledRulesEnabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<AutomationRule> dueRules = ruleRepository.findScheduledRulesDue(now);

        if (dueRules.isEmpty()) {
            log.trace("No scheduled rules due for execution at {}", now);
            return;
        }

        log.info("Found {} scheduled rules due for execution", dueRules.size());

        for (AutomationRule rule : dueRules) {
            try {
                executeScheduledRule(rule);
            } catch (Exception e) {
                log.error("Failed to execute scheduled rule '{}' (id={}): {}",
                    rule.getName(), rule.getId(), e.getMessage(), e);
                rule.incrementFailureCount();
                // Still update the next run time to avoid infinite retry
                updateNextRunTime(rule);
            }
        }
    }

    /**
     * Execute a single scheduled rule against eligible documents.
     */
    private void executeScheduledRule(AutomationRule rule) {
        long startTimeMs = System.currentTimeMillis();
        log.info("Executing scheduled rule '{}' (id={}, cron={})",
            rule.getName(), rule.getId(), rule.getCronExpression());

        SecurityContext previousContext = pushRuleAuthentication(rule);
        String auditActor = resolveRuleActor(rule);

        LocalDateTime since = rule.getLastRunAt();
        if (since == null) {
            // First run: process documents from last 24 hours
            since = LocalDateTime.now().minusHours(24);
        }

        int maxItems = rule.getMaxItemsPerRun() != null ? rule.getMaxItemsPerRun() : 200;
        PageRequest pageRequest = PageRequest.of(0, maxItems);

        Page<Document> candidateDocuments;
        if (rule.getScopeFolderId() != null) {
            candidateDocuments = documentRepository.findModifiedSinceInFolder(
                since, rule.getScopeFolderId(), pageRequest);
        } else {
            candidateDocuments = documentRepository.findModifiedSince(since, pageRequest);
        }

        log.info("Found {} candidate documents for scheduled rule '{}'",
            candidateDocuments.getTotalElements(), rule.getName());

        int processedCount = 0;
        int matchedCount = 0;
        int failedCount = 0;

        try {
            for (Document document : candidateDocuments.getContent()) {
                try {
                    // Check MIME type scope
                    if (!rule.isMimeTypeInScope(document.getMimeType())) {
                        continue;
                    }

                    processedCount++;

                    // Evaluate and execute the rule for this document
                    List<RuleExecutionResult> results = ruleEngineService.evaluateAndExecute(
                        document, TriggerType.SCHEDULED, List.of(rule));

                    for (RuleExecutionResult result : results) {
                        if (result.isConditionMatched()) {
                            matchedCount++;
                            if (!result.isSuccess()) {
                                failedCount++;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing document {} for scheduled rule '{}': {}",
                        document.getId(), rule.getName(), e.getMessage());
                    failedCount++;
                }
            }
        } finally {
            popRuleAuthentication(previousContext);
        }

        long durationMs = System.currentTimeMillis() - startTimeMs;
        log.info("Scheduled rule '{}' completed: processed={}, matched={}, failed={}, duration={}ms",
            rule.getName(), processedCount, matchedCount, failedCount, durationMs);

        // Audit log: batch execution summary (high-level, avoids per-document log explosion)
        try {
            auditService.logScheduledRuleBatchExecution(
                rule, processedCount, matchedCount - failedCount, failedCount, durationMs, auditActor);
        } catch (Exception e) {
            log.warn("Failed to write scheduled rule batch audit log: {}", e.getMessage());
        }

        // Update rule statistics and next run time
        updateNextRunTime(rule);
    }

    /**
     * Calculate and update the next run time based on the cron expression.
     */
    private void updateNextRunTime(AutomationRule rule) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = null;

        try {
            if (rule.getCronExpression() != null && !rule.getCronExpression().isBlank()) {
                CronExpression cron = CronExpression.parse(rule.getCronExpression());
                String timezone = rule.getTimezone() != null ? rule.getTimezone() : "UTC";

                // Calculate next execution time
                nextRun = cron.next(now.atZone(ZoneId.of(timezone)).toLocalDateTime());
            }
        } catch (Exception e) {
            log.error("Invalid cron expression '{}' for rule '{}': {}",
                rule.getCronExpression(), rule.getName(), e.getMessage());
            // Disable the rule if cron is invalid
            rule.setEnabled(false);
        }

        ruleRepository.updateScheduledRunTimes(rule.getId(), now, nextRun);
        log.debug("Updated scheduled rule '{}': lastRun={}, nextRun={}",
            rule.getName(), now, nextRun);
    }

    /**
     * Manually trigger a scheduled rule (for testing or admin purposes).
     */
    @Transactional
    public void triggerRule(AutomationRule rule) {
        if (!rule.isScheduledRule()) {
            throw new IllegalArgumentException("Rule is not a scheduled rule: " + rule.getId());
        }
        executeScheduledRule(rule);
    }

    /**
     * Validate a cron expression and return the next 5 execution times.
     */
    public List<LocalDateTime> validateCronExpression(String cronExpression, String timezone) {
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            String tz = timezone != null ? timezone : "UTC";
            ZoneId zoneId = ZoneId.of(tz);

            LocalDateTime current = LocalDateTime.now();
            return java.util.stream.Stream.iterate(
                    cron.next(current.atZone(zoneId).toLocalDateTime()),
                    prev -> prev != null,
                    prev -> cron.next(prev)
                )
                .limit(5)
                .toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression, e);
        }
    }

    private SecurityContext pushRuleAuthentication(AutomationRule rule) {
        String actor = resolveRuleActor(rule);
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        if ("system".equalsIgnoreCase(actor)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        SecurityContext previous = SecurityContextHolder.getContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            actor,
            "scheduled-rule",
            authorities
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        return previous;
    }

    private void popRuleAuthentication(SecurityContext previous) {
        if (previous != null) {
            SecurityContextHolder.setContext(previous);
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveRuleActor(AutomationRule rule) {
        String owner = rule.getOwner();
        if (owner != null && !owner.isBlank()) {
            return owner;
        }
        String current = securityService.getCurrentUser();
        if (current == null || current.isBlank() || "anonymous".equalsIgnoreCase(current)) {
            return "system";
        }
        return current;
    }
}
