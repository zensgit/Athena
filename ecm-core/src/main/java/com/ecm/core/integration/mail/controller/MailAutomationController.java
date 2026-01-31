package com.ecm.core.integration.mail.controller;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.model.MailRule;
import com.ecm.core.integration.mail.model.ProcessedMail;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.integration.mail.service.MailFetcherService;
import com.ecm.core.integration.mail.service.MailOAuthService;
import com.ecm.core.integration.mail.service.MailProcessedRetentionService;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integration/mail")
@RequiredArgsConstructor
@Tag(name = "Integration: Mail Automation", description = "Manage mail accounts and rules")
@Slf4j
public class MailAutomationController {

    private final MailAccountRepository accountRepository;
    private final MailRuleRepository ruleRepository;
    private final MailFetcherService fetcherService;
    private final MailOAuthService oauthService;
    private final MailProcessedRetentionService retentionService;
    private final ProcessedMailRepository processedMailRepository;
    private final AuditService auditService;
    private final SecurityService securityService;

    // === DTOs ===

    public record MailAccountRequest(
        String name,
        String host,
        Integer port,
        String username,
        String password,
        MailAccount.SecurityType security,
        Boolean enabled,
        Integer pollIntervalMinutes,
        MailAccount.OAuthProvider oauthProvider,
        String oauthTokenEndpoint,
        String oauthTenantId,
        String oauthScope,
        String oauthCredentialKey
    ) {}

    public record MailAccountResponse(
        UUID id,
        String name,
        String host,
        Integer port,
        String username,
        MailAccount.SecurityType security,
        boolean enabled,
        Integer pollIntervalMinutes,
        MailAccount.OAuthProvider oauthProvider,
        String oauthTokenEndpoint,
        String oauthTenantId,
        String oauthScope,
        String oauthCredentialKey,
        boolean oauthEnvConfigured,
        List<String> oauthMissingEnvKeys,
        Boolean oauthConnected,
        LocalDateTime lastFetchAt,
        String lastFetchStatus,
        String lastFetchError
    ) {
        static MailAccountResponse from(
            MailAccount account,
            MailFetcherService.OAuthEnvCheckResult envCheck
        ) {
            Boolean connected = null;
            if (account.getSecurity() == MailAccount.SecurityType.OAUTH2) {
                boolean hasStoredToken = account.getOauthRefreshToken() != null
                    && !account.getOauthRefreshToken().isBlank();
                connected = account.getOauthCredentialKey() != null && !account.getOauthCredentialKey().isBlank()
                    ? envCheck.configured()
                    : hasStoredToken;
            }
            return new MailAccountResponse(
                account.getId(),
                account.getName(),
                account.getHost(),
                account.getPort(),
                account.getUsername(),
                account.getSecurity(),
                account.isEnabled(),
                account.getPollIntervalMinutes(),
                account.getOauthProvider(),
                account.getOauthTokenEndpoint(),
                account.getOauthTenantId(),
                account.getOauthScope(),
                account.getOauthCredentialKey(),
                envCheck.configured(),
                envCheck.missingEnvKeys(),
                connected,
                account.getLastFetchAt(),
                account.getLastFetchStatus(),
                account.getLastFetchError()
            );
        }
    }

    public record MailRuleRequest(
        String name,
        UUID accountId,
        Integer priority,
        Boolean enabled,
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

    public record MailRulePreviewRequest(
        UUID accountId,
        Integer maxMessagesPerFolder
    ) {}

    public record MailRuleResponse(
        UUID id,
        String name,
        UUID accountId,
        Integer priority,
        Boolean enabled,
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
                rule.getEnabled(),
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
            .map(account -> MailAccountResponse.from(account, fetcherService.checkOAuthEnv(account)))
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
        account.setOauthProvider(request.oauthProvider());
        account.setOauthTokenEndpoint(request.oauthTokenEndpoint());
        account.setOauthTenantId(request.oauthTenantId());
        account.setOauthScope(request.oauthScope());
        account.setOauthCredentialKey(request.oauthCredentialKey());
        applyOAuthSettings(account);
        MailAccount saved = accountRepository.save(account);
        return ResponseEntity.ok(MailAccountResponse.from(saved, fetcherService.checkOAuthEnv(saved)));
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
        if (request.oauthProvider() != null) account.setOauthProvider(request.oauthProvider());
        if (request.oauthTokenEndpoint() != null) account.setOauthTokenEndpoint(request.oauthTokenEndpoint());
        if (request.oauthTenantId() != null) account.setOauthTenantId(request.oauthTenantId());
        if (request.oauthScope() != null) account.setOauthScope(request.oauthScope());
        if (request.oauthCredentialKey() != null) account.setOauthCredentialKey(request.oauthCredentialKey());
        applyOAuthSettings(account);

        MailAccount saved = accountRepository.save(account);
        return ResponseEntity.ok(MailAccountResponse.from(saved, fetcherService.checkOAuthEnv(saved)));
    }

    private void applyOAuthSettings(MailAccount account) {
        if (account.getSecurity() == MailAccount.SecurityType.OAUTH2) {
            boolean hasCredentialKey = account.getOauthCredentialKey() != null
                && !account.getOauthCredentialKey().isBlank();
            if (!hasCredentialKey) {
                if (account.getOauthProvider() == null || account.getOauthProvider() == MailAccount.OAuthProvider.CUSTOM) {
                    throw new IllegalArgumentException("OAuth provider is required when no credential key is set");
                }
            }
            account.setPassword(null);
            if (hasCredentialKey) {
                clearOAuthSecrets(account);
            }
        } else {
            account.setOauthProvider(null);
            account.setOauthTokenEndpoint(null);
            account.setOauthTenantId(null);
            account.setOauthScope(null);
            account.setOauthCredentialKey(null);
        }

        clearOAuthSecrets(account);
    }

    private void clearOAuthSecrets(MailAccount account) {
        account.setOauthClientId(null);
        account.setOauthClientSecret(null);
        account.setOauthAccessToken(null);
        account.setOauthRefreshToken(null);
        account.setOauthTokenExpiresAt(null);
    }

    @GetMapping("/oauth/authorize")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Start OAuth connect", description = "Generate OAuth authorization URL for an account")
    public ResponseEntity<MailOAuthService.OAuthAuthorizeResponse> authorizeOAuth(
        @RequestParam UUID accountId,
        @RequestParam(required = false) String redirectUrl,
        @RequestHeader(value = "X-Forwarded-Proto", required = false) String forwardedProto,
        @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost
    ) {
        String callbackUrl = resolveCallbackUrl(forwardedProto, forwardedHost);
        return ResponseEntity.ok(oauthService.buildAuthorizeUrl(accountId, callbackUrl, redirectUrl));
    }

    @GetMapping("/oauth/callback")
    @Operation(summary = "OAuth callback", description = "Handle OAuth callback and persist refresh token")
    public ResponseEntity<Void> oauthCallback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state
    ) {
        try {
            MailOAuthService.OAuthCallbackResult result = oauthService.handleCallback(code, state);
            String redirect = buildRedirect(result.redirectUrl(), true, result.accountId());
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, redirect).build();
        } catch (Exception ex) {
            log.warn("OAuth callback failed", ex);
            String redirect = buildRedirect(null, false, null);
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, redirect).build();
        }
    }

    private String resolveCallbackUrl(String forwardedProto, String forwardedHost) {
        if (forwardedProto != null && forwardedHost != null) {
            return forwardedProto + "://" + forwardedHost + "/api/v1/integration/mail/oauth/callback";
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/integration/mail/oauth/callback")
            .toUriString();
    }

    private String buildRedirect(String redirectUrl, boolean success, UUID accountId) {
        String base = redirectUrl != null && !redirectUrl.isBlank() ? redirectUrl : "/admin/mail";
        String suffix = base.contains("?") ? "&" : "?";
        String accountPart = accountId != null ? "&account_id=" + accountId : "";
        return base + suffix + "oauth_success=" + (success ? "1" : "0") + accountPart;
    }

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        accountRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Test connection", description = "Test connectivity for a mail account")
    public ResponseEntity<MailFetcherService.MailConnectionTestResult> testAccountConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(fetcherService.testConnection(id));
    }

    @GetMapping("/accounts/{id}/folders")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List folders", description = "List available IMAP folders for a mail account")
    public ResponseEntity<List<String>> listAccountFolders(@PathVariable UUID id) {
        return ResponseEntity.ok(fetcherService.listFolders(id));
    }

    @GetMapping("/diagnostics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mail diagnostics", description = "Recent processed messages and mail-ingested documents")
    public ResponseEntity<MailFetcherService.MailDiagnosticsResult> getDiagnostics(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) UUID accountId,
        @RequestParam(required = false) UUID ruleId,
        @RequestParam(required = false) ProcessedMail.Status status,
        @RequestParam(required = false) String subject,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime processedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime processedTo
    ) {
        return ResponseEntity.ok(
            fetcherService.getDiagnostics(limit, accountId, ruleId, status, subject, processedFrom, processedTo)
        );
    }

    @GetMapping("/diagnostics/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export mail diagnostics", description = "Export recent mail diagnostics as CSV")
    public ResponseEntity<String> exportDiagnostics(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) UUID accountId,
        @RequestParam(required = false) UUID ruleId,
        @RequestParam(required = false) ProcessedMail.Status status,
        @RequestParam(required = false) String subject,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime processedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime processedTo,
        @RequestParam(required = false) Boolean includeProcessed,
        @RequestParam(required = false) Boolean includeDocuments,
        @RequestParam(required = false) Boolean includeSubject,
        @RequestParam(required = false) Boolean includeError,
        @RequestParam(required = false) Boolean includePath,
        @RequestParam(required = false) Boolean includeMimeType,
        @RequestParam(required = false) Boolean includeFileSize
    ) {
        MailDiagnosticsExportAuditOptions auditOptions = resolveExportAuditOptions(
            includeProcessed,
            includeDocuments,
            includeSubject,
            includeError,
            includePath,
            includeMimeType,
            includeFileSize
        );
        String csv = fetcherService.exportDiagnosticsCsv(
            limit,
            accountId,
            ruleId,
            status,
            subject,
            processedFrom,
            processedTo,
            includeProcessed,
            includeDocuments,
            includeSubject,
            includeError,
            includePath,
            includeMimeType,
            includeFileSize
        );
        auditDiagnosticsExport(limit, accountId, ruleId, auditOptions);
        String filename = "mail-diagnostics-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.valueOf("text/csv"))
            .body(csv);
    }

    @PostMapping("/processed/bulk-delete")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bulk delete processed mail", description = "Delete processed mail records by id")
    public ResponseEntity<ProcessedMailBulkDeleteResult> bulkDeleteProcessed(
        @RequestBody ProcessedMailBulkDeleteRequest request
    ) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            return ResponseEntity.ok(new ProcessedMailBulkDeleteResult(0));
        }
        processedMailRepository.deleteAllById(request.ids());
        return ResponseEntity.ok(new ProcessedMailBulkDeleteResult(request.ids().size()));
    }

    @GetMapping("/processed/retention")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Processed mail retention", description = "Get retention policy for processed mail")
    public ResponseEntity<ProcessedMailRetentionStatus> getProcessedRetention() {
        int retentionDays = retentionService.getRetentionDays();
        boolean enabled = retentionService.isRetentionEnabled();
        long expiredCount = retentionService.getExpiredCount();
        return ResponseEntity.ok(new ProcessedMailRetentionStatus(retentionDays, enabled, expiredCount));
    }

    @PostMapping("/processed/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cleanup processed mail", description = "Delete expired processed mail based on retention")
    public ResponseEntity<ProcessedMailCleanupResult> cleanupProcessedMail() {
        long deleted = retentionService.manualCleanupExpiredProcessedMail();
        return ResponseEntity.ok(new ProcessedMailCleanupResult(deleted));
    }

    private void auditDiagnosticsExport(
        Integer limit,
        UUID accountId,
        UUID ruleId,
        MailDiagnosticsExportAuditOptions exportOptions
    ) {
        int effectiveLimit = resolveEffectiveLimit(limit);
        UUID auditNodeId = accountId != null ? accountId : ruleId;
        String auditNodeName = accountId != null
            ? "MAIL_ACCOUNT"
            : (ruleId != null ? "MAIL_RULE" : "MAIL_DIAGNOSTICS");
        String details = String.format(
            "Exported mail diagnostics (limit=%d, accountId=%s, ruleId=%s, includeProcessed=%s, " +
                "includeDocuments=%s, includeSubject=%s, includeError=%s, includePath=%s, " +
                "includeMimeType=%s, includeFileSize=%s)",
            effectiveLimit,
            accountId != null ? accountId : "ALL",
            ruleId != null ? ruleId : "ALL",
            exportOptions.includeProcessed(),
            exportOptions.includeDocuments(),
            exportOptions.includeSubject(),
            exportOptions.includeError(),
            exportOptions.includePath(),
            exportOptions.includeMimeType(),
            exportOptions.includeFileSize()
        );
        auditService.logEvent(
            "MAIL_DIAGNOSTICS_EXPORTED",
            auditNodeId,
            auditNodeName,
            resolveAuditUsername(),
            details
        );
    }

    private MailDiagnosticsExportAuditOptions resolveExportAuditOptions(
        Boolean includeProcessed,
        Boolean includeDocuments,
        Boolean includeSubject,
        Boolean includeError,
        Boolean includePath,
        Boolean includeMimeType,
        Boolean includeFileSize
    ) {
        return new MailDiagnosticsExportAuditOptions(
            includeProcessed == null || includeProcessed,
            includeDocuments == null || includeDocuments,
            includeSubject == null || includeSubject,
            includeError == null || includeError,
            includePath == null || includePath,
            includeMimeType == null || includeMimeType,
            includeFileSize == null || includeFileSize
        );
    }

    private int resolveEffectiveLimit(Integer limit) {
        return Math.max(1, Math.min(limit != null ? limit : 25, 200));
    }

    private String resolveAuditUsername() {
        String username = securityService.getCurrentUser();
        return username == null || username.isBlank() ? "unknown" : username;
    }

    private record MailDiagnosticsExportAuditOptions(
        boolean includeProcessed,
        boolean includeDocuments,
        boolean includeSubject,
        boolean includeError,
        boolean includePath,
        boolean includeMimeType,
        boolean includeFileSize
    ) {
    }

    public record ProcessedMailBulkDeleteRequest(List<UUID> ids) {}

    public record ProcessedMailBulkDeleteResult(int deleted) {}

    public record ProcessedMailRetentionStatus(int retentionDays, boolean enabled, long expiredCount) {}

    public record ProcessedMailCleanupResult(long deleted) {}

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
        rule.setEnabled(request.enabled() == null || request.enabled());
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
        if (request.enabled() != null) rule.setEnabled(request.enabled());
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
    public ResponseEntity<MailFetcherService.MailFetchSummary> triggerFetch() {
        return ResponseEntity.ok(fetcherService.fetchAllAccounts(true));
    }

    @PostMapping("/fetch/debug")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Debug fetch (dry run)",
        description = "Dry-run mail fetch to diagnose matching and skip reasons without ingesting content"
    )
    public ResponseEntity<MailFetcherService.MailFetchDebugResult> triggerFetchDebug(
        @RequestParam(name = "force", defaultValue = "true") boolean force,
        @RequestParam(name = "maxMessagesPerFolder", required = false) Integer maxMessagesPerFolder
    ) {
        return ResponseEntity.ok(fetcherService.fetchAllAccountsDebug(force, maxMessagesPerFolder));
    }

    @PostMapping("/rules/{id}/preview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Preview rule (dry run)",
        description = "Dry-run a single mail rule match without ingesting content"
    )
    public ResponseEntity<MailFetcherService.MailRulePreviewResult> previewRule(
        @PathVariable UUID id,
        @RequestBody MailRulePreviewRequest request
    ) {
        if (request == null || request.accountId() == null) {
            throw new IllegalArgumentException("accountId is required to preview a rule");
        }
        return ResponseEntity.ok(fetcherService.previewRule(request.accountId(), id, request.maxMessagesPerFolder()));
    }
}
