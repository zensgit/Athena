package com.ecm.core.integration.mail.controller;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.model.MailRule;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.service.MailFetcherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integration/mail")
@RequiredArgsConstructor
@Tag(name = "Integration: Mail Automation", description = "Manage mail accounts and rules")
public class MailAutomationController {

    private final MailAccountRepository accountRepository;
    private final MailRuleRepository ruleRepository;
    private final MailFetcherService fetcherService;

    // === DTOs ===

    public record MailAccountRequest(
        String name,
        String host,
        Integer port,
        String username,
        String password,
        MailAccount.SecurityType security,
        Boolean enabled,
        Integer pollIntervalMinutes
    ) {}

    public record MailAccountResponse(
        UUID id,
        String name,
        String host,
        Integer port,
        String username,
        MailAccount.SecurityType security,
        boolean enabled,
        Integer pollIntervalMinutes
    ) {
        static MailAccountResponse from(MailAccount account) {
            return new MailAccountResponse(
                account.getId(),
                account.getName(),
                account.getHost(),
                account.getPort(),
                account.getUsername(),
                account.getSecurity(),
                account.isEnabled(),
                account.getPollIntervalMinutes()
            );
        }
    }

    public record MailRuleRequest(
        String name,
        UUID accountId,
        Integer priority,
        String folder,
        String subjectFilter,
        String fromFilter,
        String toFilter,
        String bodyFilter,
        String attachmentFilenameInclude,
        String attachmentFilenameExclude,
        Integer maxAgeDays,
        Boolean includeInlineAttachments,
        MailRule.MailActionType actionType,
        MailRule.MailPostAction mailAction,
        String mailActionParam,
        UUID assignTagId,
        UUID assignFolderId
    ) {}

    public record MailRuleResponse(
        UUID id,
        String name,
        UUID accountId,
        Integer priority,
        String folder,
        String subjectFilter,
        String fromFilter,
        String toFilter,
        String bodyFilter,
        String attachmentFilenameInclude,
        String attachmentFilenameExclude,
        Integer maxAgeDays,
        Boolean includeInlineAttachments,
        MailRule.MailActionType actionType,
        MailRule.MailPostAction mailAction,
        String mailActionParam,
        UUID assignTagId,
        UUID assignFolderId
    ) {
        static MailRuleResponse from(MailRule rule) {
            return new MailRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getAccountId(),
                rule.getPriority(),
                rule.getFolder(),
                rule.getSubjectFilter(),
                rule.getFromFilter(),
                rule.getToFilter(),
                rule.getBodyFilter(),
                rule.getAttachmentFilenameInclude(),
                rule.getAttachmentFilenameExclude(),
                rule.getMaxAgeDays(),
                rule.getIncludeInlineAttachments(),
                rule.getActionType(),
                rule.getMailAction(),
                rule.getMailActionParam(),
                rule.getAssignTagId(),
                rule.getAssignFolderId()
            );
        }
    }

    // === Accounts ===

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MailAccountResponse>> getAccounts() {
        List<MailAccountResponse> accounts = accountRepository.findAll().stream()
            .map(MailAccountResponse::from)
            .toList();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MailAccountResponse> createAccount(@RequestBody MailAccountRequest request) {
        MailAccount account = new MailAccount();
        account.setName(request.name());
        account.setHost(request.host());
        account.setPort(request.port());
        account.setUsername(request.username());
        account.setPassword(request.password());
        account.setSecurity(request.security() != null ? request.security() : MailAccount.SecurityType.SSL);
        account.setEnabled(request.enabled() != null ? request.enabled() : true);
        account.setPollIntervalMinutes(request.pollIntervalMinutes() != null ? request.pollIntervalMinutes() : 10);
        return ResponseEntity.ok(MailAccountResponse.from(accountRepository.save(account)));
    }

    @PutMapping("/accounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MailAccountResponse> updateAccount(@PathVariable UUID id, @RequestBody MailAccountRequest request) {
        MailAccount account = accountRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Mail account not found: " + id));

        if (request.name() != null) account.setName(request.name());
        if (request.host() != null) account.setHost(request.host());
        if (request.port() != null) account.setPort(request.port());
        if (request.username() != null) account.setUsername(request.username());
        if (request.password() != null && !request.password().isBlank()) account.setPassword(request.password());
        if (request.security() != null) account.setSecurity(request.security());
        if (request.enabled() != null) account.setEnabled(request.enabled());
        if (request.pollIntervalMinutes() != null) account.setPollIntervalMinutes(request.pollIntervalMinutes());

        return ResponseEntity.ok(MailAccountResponse.from(accountRepository.save(account)));
    }

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        accountRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // === Rules ===

    @GetMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MailRuleResponse>> getRules() {
        List<MailRuleResponse> rules = ruleRepository.findAllByOrderByPriorityAsc().stream()
            .map(MailRuleResponse::from)
            .toList();
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MailRuleResponse> createRule(@RequestBody MailRuleRequest request) {
        MailRule rule = new MailRule();
        rule.setName(request.name());
        rule.setAccountId(request.accountId());
        rule.setPriority(request.priority() != null ? request.priority() : 100);
        rule.setFolder(request.folder() != null && !request.folder().isBlank() ? request.folder() : "INBOX");
        rule.setSubjectFilter(request.subjectFilter());
        rule.setFromFilter(request.fromFilter());
        rule.setToFilter(request.toFilter());
        rule.setBodyFilter(request.bodyFilter());
        rule.setAttachmentFilenameInclude(request.attachmentFilenameInclude());
        rule.setAttachmentFilenameExclude(request.attachmentFilenameExclude());
        rule.setMaxAgeDays(request.maxAgeDays());
        rule.setIncludeInlineAttachments(request.includeInlineAttachments() != null && request.includeInlineAttachments());
        rule.setActionType(request.actionType() != null ? request.actionType() : MailRule.MailActionType.ATTACHMENTS_ONLY);
        rule.setMailAction(request.mailAction() != null ? request.mailAction() : MailRule.MailPostAction.MARK_READ);
        rule.setMailActionParam(request.mailActionParam());
        rule.setAssignTagId(request.assignTagId());
        rule.setAssignFolderId(request.assignFolderId());
        return ResponseEntity.ok(MailRuleResponse.from(ruleRepository.save(rule)));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MailRuleResponse> updateRule(@PathVariable UUID id, @RequestBody MailRuleRequest request) {
        MailRule rule = ruleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Mail rule not found: " + id));

        if (request.name() != null) rule.setName(request.name());
        if (request.accountId() != null) rule.setAccountId(request.accountId());
        if (request.priority() != null) rule.setPriority(request.priority());
        if (request.folder() != null) {
            rule.setFolder(request.folder().isBlank() ? "INBOX" : request.folder());
        }
        if (request.subjectFilter() != null) rule.setSubjectFilter(request.subjectFilter());
        if (request.fromFilter() != null) rule.setFromFilter(request.fromFilter());
        if (request.toFilter() != null) rule.setToFilter(request.toFilter());
        if (request.bodyFilter() != null) rule.setBodyFilter(request.bodyFilter());
        if (request.attachmentFilenameInclude() != null) rule.setAttachmentFilenameInclude(request.attachmentFilenameInclude());
        if (request.attachmentFilenameExclude() != null) rule.setAttachmentFilenameExclude(request.attachmentFilenameExclude());
        if (request.maxAgeDays() != null) rule.setMaxAgeDays(request.maxAgeDays());
        if (request.includeInlineAttachments() != null) {
            rule.setIncludeInlineAttachments(request.includeInlineAttachments());
        }
        if (request.actionType() != null) rule.setActionType(request.actionType());
        if (request.mailAction() != null) rule.setMailAction(request.mailAction());
        if (request.mailActionParam() != null) rule.setMailActionParam(request.mailActionParam());
        if (request.assignTagId() != null) rule.setAssignTagId(request.assignTagId());
        if (request.assignFolderId() != null) rule.setAssignFolderId(request.assignFolderId());

        return ResponseEntity.ok(MailRuleResponse.from(ruleRepository.save(rule)));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        ruleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // === Actions ===

    @PostMapping("/fetch")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger fetch", description = "Manually trigger mail fetching")
    public ResponseEntity<Void> triggerFetch() {
        fetcherService.fetchAllAccounts(true);
        return ResponseEntity.ok().build();
    }
}
