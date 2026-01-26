package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.email.EmailIngestionService;
import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.model.MailRule;
import com.ecm.core.integration.mail.model.ProcessedMail;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.TagService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service to fetch emails via IMAP and process them according to rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailFetcherService {

    private static final int DEFAULT_POLL_INTERVAL_MINUTES = 10;

    private final MailAccountRepository accountRepository;
    private final MailRuleRepository ruleRepository;
    private final ProcessedMailRepository processedMailRepository;
    private final DocumentUploadService uploadService;
    private final TagService tagService;
    private final EmailIngestionService emailIngestionService;
    private final MeterRegistry meterRegistry;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ecm.mail.fetcher.run-as-user:admin}")
    private String runAsUser;

    private final Map<UUID, Instant> lastPollByAccount = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${ecm.mail.fetcher.poll-interval-ms:60000}")
    public void fetchAllAccounts() {
        fetchAllAccounts(false);
    }

    public MailFetchSummary fetchAllAccounts(boolean force) {
        List<MailAccount> accounts = accountRepository.findByEnabledTrue();
        log.info("Starting mail fetch for {} accounts (force={})", accounts.size(), force);

        MailFetchRunStats stats = new MailFetchRunStats();
        stats.accounts = accounts.size();
        long startNs = System.nanoTime();

        Timer.Sample runSample = Timer.start(meterRegistry);
        for (MailAccount account : accounts) {
            Instant now = Instant.now();
            if (!force && !shouldProcessAccount(account, now)) {
                stats.skippedAccounts++;
                incrementAccountMetric("skipped", "poll_interval");
                continue;
            }

            stats.attemptedAccounts++;
            String status = "ok";
            String reason = "none";
            Timer.Sample accountSample = Timer.start(meterRegistry);
            try {
                runWithSystemAuthenticationIfMissing(() -> processAccount(account, stats));
            } catch (Exception e) {
                status = "error";
                reason = "exception";
                stats.accountErrors++;
                log.error("Failed to process mail account: {}", account.getName(), e);
            } finally {
                lastPollByAccount.put(account.getId(), now);
                accountSample.stop(meterRegistry.timer("mail_fetch_account_duration", "status", status));
                incrementAccountMetric(status, reason);
            }
        }

        runSample.stop(meterRegistry.timer("mail_fetch_run_duration"));
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        log.info("Mail fetch completed: accounts={}, attempted={}, skipped={}, force={}",
            accounts.size(), stats.attemptedAccounts, stats.skippedAccounts, force);

        return stats.toSummary(durationMs);
    }

    private void processAccount(MailAccount account, MailFetchRunStats stats) throws Exception {
        Store store = connect(account);

        List<MailRule> rules = ruleRepository.findAllByOrderByPriorityAsc().stream()
            .filter(rule -> rule.getAccountId() == null || Objects.equals(rule.getAccountId(), account.getId()))
            .collect(Collectors.toList());

        if (rules.isEmpty()) {
            log.debug("No mail rules configured for account {}", account.getName());
            store.close();
            return;
        }

        Map<String, List<MailRule>> rulesByFolder = rules.stream()
            .collect(Collectors.groupingBy(rule -> normalizeFolder(rule.getFolder())));

        for (Map.Entry<String, List<MailRule>> entry : rulesByFolder.entrySet()) {
            processFolder(store, account, entry.getKey(), entry.getValue(), stats);
        }

        store.close();
    }

    private void processFolder(Store store, MailAccount account, String folderName, List<MailRule> rules,
            MailFetchRunStats stats)
            throws Exception {
        Folder folder = store.getFolder(folderName);
        if (!folder.exists()) {
            log.warn("Mail folder '{}' does not exist for account {}", folderName, account.getName());
            return;
        }

        folder.open(Folder.READ_WRITE);

        Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        log.info("Found {} unread messages in {} ({})", messages.length, account.getName(), folderName);
        stats.foundMessages += messages.length;

        boolean expungeNeeded = false;
        for (Message message : messages) {
            try {
                expungeNeeded |= processMessage(message, rules, account, folderName, folder, stats);
            } catch (Exception e) {
                log.error("Error processing message: {}", safeSubject(message), e);
            }
        }

        if (expungeNeeded) {
            folder.expunge();
        }
        folder.close(false);
    }

    private boolean processMessage(Message message, List<MailRule> rules, MailAccount account, String folderName,
            Folder folder, MailFetchRunStats stats) throws Exception {
        String uid = resolveMessageUid(folder, message);
        if (processedMailRepository.existsByAccountIdAndFolderAndUid(account.getId(), folderName, uid)) {
            log.debug("Skipping already processed mail UID {} in {}", uid, folderName);
            incrementMessageMetric("skipped", "already_processed");
            stats.skippedMessages++;
            return false;
        }

        List<AttachmentPart> attachments = collectAttachmentParts(message);
        List<String> attachmentNames = attachments.stream()
            .map(AttachmentPart::fileName)
            .filter(name -> name != null && !name.isBlank())
            .collect(Collectors.toList());

        MailRuleMatcher.MailMessageData messageData = new MailRuleMatcher.MailMessageData(
            safeSubject(message),
            safeFrom(message),
            safeRecipients(message),
            extractBodyText(message),
            attachmentNames,
            toLocalDateTime(message.getReceivedDate(), message.getSentDate())
        );

        MailRule matchedRule = findMatchingRule(rules, messageData);
        if (matchedRule == null) {
            log.debug("No rule matched for email in {}", folderName);
            incrementMessageMetric("skipped", "no_rule");
            stats.skippedMessages++;
            return false;
        }

        log.info("Email matched rule: {}", matchedRule.getName());
        stats.matchedMessages++;

        boolean processed = false;
        try {
            processed = processContent(message, matchedRule, attachments);
            if (!processed) {
                log.debug("Rule matched but no content processed for UID {}", uid);
                incrementMessageMetric("skipped", "no_content");
                stats.skippedMessages++;
                return false;
            }

            boolean expungeNeeded = applyMailAction(matchedRule, folder, message);
            recordProcessedMail(account, folderName, uid, matchedRule, message, ProcessedMail.Status.PROCESSED, null);
            incrementMessageMetric("processed", "content");
            stats.processedMessages++;
            return expungeNeeded;
        } catch (Exception e) {
            recordProcessedMail(account, folderName, uid, matchedRule, message, ProcessedMail.Status.ERROR,
                e.getMessage());
            incrementMessageMetric("error", "exception");
            stats.errorMessages++;
            throw e;
        }
    }

    public MailConnectionTestResult testConnection(UUID accountId) {
        MailAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Mail account not found: " + accountId));
        return testConnection(account);
    }

    public MailConnectionTestResult testConnection(MailAccount account) {
        long startNs = System.nanoTime();
        Store store = null;
        try {
            store = connect(account);
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new MailConnectionTestResult(true, "Connected", durationMs);
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Mail connection test failed for {}: {}", account.getName(), message);
            return new MailConnectionTestResult(false, message, durationMs);
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (Exception ignored) {
                    // best effort
                }
            }
        }
    }

    public record MailConnectionTestResult(boolean success, String message, long durationMs) {
    }

    public record MailFetchSummary(
        int accounts,
        int attemptedAccounts,
        int skippedAccounts,
        int accountErrors,
        int foundMessages,
        int matchedMessages,
        int processedMessages,
        int skippedMessages,
        int errorMessages,
        long durationMs
    ) {
    }

    private static final class MailFetchRunStats {
        private int accounts;
        private int attemptedAccounts;
        private int skippedAccounts;
        private int accountErrors;
        private int foundMessages;
        private int matchedMessages;
        private int processedMessages;
        private int skippedMessages;
        private int errorMessages;

        private MailFetchSummary toSummary(long durationMs) {
            return new MailFetchSummary(
                accounts,
                attemptedAccounts,
                skippedAccounts,
                accountErrors,
                foundMessages,
                matchedMessages,
                processedMessages,
                skippedMessages,
                errorMessages,
                durationMs
            );
        }
    }

    private MailRule findMatchingRule(List<MailRule> rules, MailRuleMatcher.MailMessageData messageData) {
        for (MailRule rule : rules) {
            if (MailRuleMatcher.matches(rule, messageData)) {
                return rule;
            }
        }
        return null;
    }

    private boolean processContent(Message message, MailRule rule, List<AttachmentPart> attachments) throws Exception {
        boolean processed = false;
        MailRule.MailActionType actionType = rule.getActionType() != null
            ? rule.getActionType()
            : MailRule.MailActionType.ATTACHMENTS_ONLY;

        if (actionType == MailRule.MailActionType.METADATA_ONLY
            || actionType == MailRule.MailActionType.EVERYTHING) {
            processed |= ingestEmailMessage(message, rule);
        }

        if (actionType == MailRule.MailActionType.ATTACHMENTS_ONLY
            || actionType == MailRule.MailActionType.EVERYTHING) {
            processed |= ingestAttachments(attachments, rule);
        }

        return processed;
    }

    private boolean ingestEmailMessage(Message message, MailRule rule) throws Exception {
        MultipartFile file = buildEmlFile(message);
        var document = emailIngestionService.ingestEmail(file, rule.getAssignFolderId());
        if (document != null && rule.getAssignTagId() != null) {
            tagService.addTagToNodeById(document.getId().toString(), rule.getAssignTagId());
        }
        return document != null;
    }

    private boolean ingestAttachments(List<AttachmentPart> attachments, MailRule rule) throws Exception {
        boolean includeInline = Boolean.TRUE.equals(rule.getIncludeInlineAttachments());
        boolean processed = false;

        for (AttachmentPart attachment : attachments) {
            if (!includeInline && attachment.inline()) {
                continue;
            }

            if (!attachmentMatchesRule(attachment.fileName(), rule)) {
                continue;
            }

            MultipartFile file = new MockMultipartFile(
                attachment.fileName(),
                attachment.fileName(),
                attachment.part().getContentType(),
                IOUtils.toByteArray(attachment.part().getInputStream())
            );

            var result = uploadService.uploadDocument(file, rule.getAssignFolderId(), null);
            if (result.isSuccess()) {
                processed = true;
                if (rule.getAssignTagId() != null) {
                    tagService.addTagToNodeById(result.getDocumentId().toString(), rule.getAssignTagId());
                }
            }
        }

        return processed;
    }

    private boolean attachmentMatchesRule(String fileName, MailRule rule) {
        List<String> names = fileName != null ? List.of(fileName) : List.of();
        return MailRuleMatcher.matchesAttachmentFilters(rule, names);
    }

    private boolean applyMailAction(MailRule rule, Folder folder, Message message) throws Exception {
        MailRule.MailPostAction action = rule.getMailAction() != null
            ? rule.getMailAction()
            : MailRule.MailPostAction.MARK_READ;

        switch (action) {
            case NONE -> {
                return false;
            }
            case MARK_READ -> {
                message.setFlag(Flags.Flag.SEEN, true);
                return false;
            }
            case FLAG -> {
                message.setFlag(Flags.Flag.FLAGGED, true);
                return false;
            }
            case DELETE -> {
                message.setFlag(Flags.Flag.DELETED, true);
                return true;
            }
            case MOVE -> {
                String targetFolder = rule.getMailActionParam();
                if (targetFolder == null || targetFolder.isBlank()) {
                    log.warn("Mail action MOVE requires mailActionParam");
                    return false;
                }
                moveMessage(folder, message, targetFolder);
                return true;
            }
            case TAG -> {
                String tag = rule.getMailActionParam();
                if (tag == null || tag.isBlank()) {
                    log.warn("Mail action TAG requires mailActionParam");
                    return false;
                }
                message.setFlags(new Flags(tag), true);
                return false;
            }
        }

        return false;
    }

    private void moveMessage(Folder sourceFolder, Message message, String targetFolderName) throws Exception {
        Folder targetFolder = sourceFolder.getStore().getFolder(targetFolderName);
        if (!targetFolder.exists()) {
            targetFolder.create(Folder.HOLDS_MESSAGES);
        }
        sourceFolder.copyMessages(new Message[]{message}, targetFolder);
        message.setFlag(Flags.Flag.DELETED, true);
    }

    private void recordProcessedMail(
        MailAccount account,
        String folderName,
        String uid,
        MailRule rule,
        Message message,
        ProcessedMail.Status status,
        String errorMessage
    ) {
        ProcessedMail processed = new ProcessedMail();
        processed.setAccountId(account.getId());
        processed.setRuleId(rule != null ? rule.getId() : null);
        processed.setFolder(folderName);
        processed.setUid(uid);
        processed.setSubject(safeSubject(message));
        processed.setReceivedAt(toLocalDateTime(safeDate(message, true), safeDate(message, false)));
        processed.setProcessedAt(LocalDateTime.now());
        processed.setStatus(status);
        processed.setErrorMessage(errorMessage);
        processedMailRepository.save(processed);
    }

    private MultipartFile buildEmlFile(Message message) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeTo(outputStream);
        String filename = buildEmailFilename(safeSubject(message));
        return new MockMultipartFile(
            filename,
            filename,
            "message/rfc822",
            outputStream.toByteArray()
        );
    }

    private List<AttachmentPart> collectAttachmentParts(Part part) throws Exception {
        List<AttachmentPart> attachments = new ArrayList<>();

        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.isMimeType("multipart/*")) {
                    attachments.addAll(collectAttachmentParts(bodyPart));
                    continue;
                }

                String fileName = bodyPart.getFileName();
                String disposition = bodyPart.getDisposition();
                boolean hasFilename = fileName != null && !fileName.isBlank();
                boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition) || hasFilename;
                if (isAttachment && hasFilename) {
                    boolean inline = Part.INLINE.equalsIgnoreCase(disposition);
                    attachments.add(new AttachmentPart(bodyPart, fileName, inline));
                }
            }
        } else if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part nestedPart) {
                attachments.addAll(collectAttachmentParts(nestedPart));
            }
        }

        return attachments;
    }

    private String extractBodyText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return safeContent(part.getContent());
        }
        if (part.isMimeType("text/html")) {
            return stripHtml(safeContent(part.getContent()));
        }
        if (part.isMimeType("multipart/*")) {
            StringBuilder builder = new StringBuilder();
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String text = extractBodyText(bodyPart);
                if (!text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
            return builder.toString();
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part nestedPart) {
                return extractBodyText(nestedPart);
            }
        }
        return "";
    }

    private String safeContent(Object content) {
        if (content == null) {
            return "";
        }
        return content.toString();
    }

    private String stripHtml(String input) {
        return input.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private String safeSubject(Message message) {
        try {
            return message.getSubject() != null ? message.getSubject() : "";
        } catch (MessagingException e) {
            return "";
        }
    }

    private String safeFrom(Message message) {
        try {
            var from = message.getFrom();
            if (from != null && from.length > 0) {
                return from[0].toString();
            }
        } catch (MessagingException ignored) {
            // fall through
        }
        return "";
    }

    private String safeRecipients(Message message) {
        try {
            Collection<String> recipients = new ArrayList<>();
            addRecipients(message, Message.RecipientType.TO, recipients);
            addRecipients(message, Message.RecipientType.CC, recipients);
            return String.join(", ", recipients);
        } catch (MessagingException e) {
            return "";
        }
    }

    private void addRecipients(Message message, Message.RecipientType type, Collection<String> recipients)
            throws MessagingException {
        var addresses = message.getRecipients(type);
        if (addresses == null) {
            return;
        }
        for (var address : addresses) {
            recipients.add(address.toString());
        }
    }

    private String resolveMessageUid(Folder folder, Message message) {
        try {
            if (folder instanceof UIDFolder uidFolder) {
                long uid = uidFolder.getUID(message);
                if (uid > 0) {
                    return Long.toString(uid);
                }
            }
            String[] headers = message.getHeader("Message-ID");
            if (headers != null && headers.length > 0) {
                return headers[0];
            }
        } catch (MessagingException ignored) {
            // fall through
        }
        String fallback = safeSubject(message) + "|" + safeFrom(message) + "|" + safeDate(message, false);
        return UUID.nameUUIDFromBytes(fallback.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private LocalDateTime toLocalDateTime(Date receivedDate, Date sentDate) {
        Date source = receivedDate != null ? receivedDate : sentDate;
        if (source == null) {
            return null;
        }
        return LocalDateTime.ofInstant(source.toInstant(), ZoneId.systemDefault());
    }

    private Date safeDate(Message message, boolean received) {
        try {
            return received ? message.getReceivedDate() : message.getSentDate();
        } catch (MessagingException e) {
            return null;
        }
    }

    private String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "INBOX";
        }
        return folder.trim();
    }

    private boolean shouldProcessAccount(MailAccount account, Instant now) {
        int pollIntervalMinutes = resolvePollIntervalMinutes(account);
        Instant lastPoll = lastPollByAccount.get(account.getId());
        if (lastPoll == null) {
            return true;
        }
        long elapsedMs = Duration.between(lastPoll, now).toMillis();
        long intervalMs = Duration.ofMinutes(pollIntervalMinutes).toMillis();
        if (elapsedMs < intervalMs) {
            log.debug("Skipping mail account {} due to poll interval ({} minutes)", account.getName(),
                pollIntervalMinutes);
            return false;
        }
        return true;
    }

    private int resolvePollIntervalMinutes(MailAccount account) {
        Integer pollInterval = account.getPollIntervalMinutes();
        if (pollInterval == null || pollInterval <= 0) {
            return DEFAULT_POLL_INTERVAL_MINUTES;
        }
        return pollInterval;
    }

    private void incrementAccountMetric(String status, String reason) {
        meterRegistry.counter("mail_fetch_accounts_total", "status", status, "reason", reason).increment();
    }

    private void incrementMessageMetric(String status, String reason) {
        meterRegistry.counter("mail_fetch_messages_total", "status", status, "reason", reason).increment();
    }

    private void runWithSystemAuthenticationIfMissing(MailFetcherTask task) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            task.run();
            return;
        }

        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext systemContext = SecurityContextHolder.createEmptyContext();
        systemContext.setAuthentication(createSystemAuthentication());
        SecurityContextHolder.setContext(systemContext);
        try {
            task.run();
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    private Authentication createSystemAuthentication() {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return new UsernamePasswordAuthenticationToken(runAsUser, "N/A", authorities);
    }

    @FunctionalInterface
    private interface MailFetcherTask {
        void run() throws Exception;
    }

    private String buildEmailFilename(String subject) {
        String base = subject != null && !subject.isBlank() ? subject : "email";
        String sanitized = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        return sanitized + ".eml";
    }

    private record AttachmentPart(BodyPart part, String fileName, boolean inline) {
    }

    private String resolveOAuthAccessToken(MailAccount account) {
        String accessToken = account.getOauthAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            if (account.getOauthRefreshToken() == null || account.getOauthRefreshToken().isBlank()) {
                throw new IllegalStateException("OAuth access token missing for account " + account.getName());
            }
            return refreshOAuthAccessToken(account).accessToken();
        }

        if (!isTokenExpired(account.getOauthTokenExpiresAt())) {
            return accessToken;
        }

        if (account.getOauthRefreshToken() == null || account.getOauthRefreshToken().isBlank()) {
            log.warn("OAuth access token expired for account {} and no refresh token provided", account.getName());
            return accessToken;
        }

        return refreshOAuthAccessToken(account).accessToken();
    }

    private OAuthTokenResponse refreshOAuthAccessToken(MailAccount account) {
        if (account.getOauthClientId() == null || account.getOauthClientId().isBlank()) {
            throw new IllegalStateException("OAuth client id missing for account " + account.getName());
        }
        if (account.getOauthRefreshToken() == null || account.getOauthRefreshToken().isBlank()) {
            throw new IllegalStateException("OAuth refresh token missing for account " + account.getName());
        }

        String tokenEndpoint = resolveOAuthTokenEndpoint(account);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", account.getOauthClientId());
        if (account.getOauthClientSecret() != null && !account.getOauthClientSecret().isBlank()) {
            form.add("client_secret", account.getOauthClientSecret());
        }
        form.add("refresh_token", account.getOauthRefreshToken());
        if (account.getOauthScope() != null && !account.getOauthScope().isBlank()) {
            form.add("scope", account.getOauthScope());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var response = restTemplate.postForEntity(tokenEndpoint, new HttpEntity<>(form, headers), Map.class);
        Map<?, ?> body = response.getBody();
        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("OAuth token refresh failed for account " + account.getName());
        }

        String accessToken = body.get("access_token").toString();
        String refreshToken = body.get("refresh_token") != null ? body.get("refresh_token").toString() : null;
        Long expiresIn = body.get("expires_in") instanceof Number number ? number.longValue() : null;

        account.setOauthAccessToken(accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            account.setOauthRefreshToken(refreshToken);
        }
        if (expiresIn != null && expiresIn > 0) {
            account.setOauthTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        }
        accountRepository.save(account);

        return new OAuthTokenResponse(accessToken, refreshToken, expiresIn);
    }

    private String resolveOAuthTokenEndpoint(MailAccount account) {
        if (account.getOauthTokenEndpoint() != null && !account.getOauthTokenEndpoint().isBlank()) {
            return account.getOauthTokenEndpoint();
        }
        if (account.getOauthProvider() == null) {
            throw new IllegalStateException("OAuth provider not configured for account " + account.getName());
        }
        return switch (account.getOauthProvider()) {
            case GOOGLE -> "https://oauth2.googleapis.com/token";
            case MICROSOFT -> {
                String tenantId = account.getOauthTenantId();
                if (tenantId == null || tenantId.isBlank()) {
                    tenantId = "common";
                }
                yield "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
            }
            case CUSTOM -> throw new IllegalStateException("OAuth token endpoint not configured for account "
                + account.getName());
        };
    }

    private boolean isTokenExpired(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return false;
        }
        return expiresAt.isBefore(LocalDateTime.now().plusMinutes(1));
    }

    private record OAuthTokenResponse(String accessToken, String refreshToken, Long expiresIn) {
    }

    private Store connect(MailAccount account) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imap");
        
        if (account.getSecurity() == MailAccount.SecurityType.SSL) {
            props.put("mail.imap.ssl.enable", "true");
        } else if (account.getSecurity() == MailAccount.SecurityType.STARTTLS) {
            props.put("mail.imap.starttls.enable", "true");
        } else if (account.getSecurity() == MailAccount.SecurityType.OAUTH2) {
            props.put("mail.imap.ssl.enable", "true");
            props.put("mail.imap.sasl.enable", "true");
            props.put("mail.imap.sasl.mechanisms", "XOAUTH2");
            props.put("mail.imap.auth.login.disable", "true");
            props.put("mail.imap.auth.plain.disable", "true");
        }

        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        if (account.getSecurity() == MailAccount.SecurityType.OAUTH2) {
            String accessToken = resolveOAuthAccessToken(account);
            store.connect(account.getHost(), account.getPort(), account.getUsername(), accessToken);
        } else {
            store.connect(account.getHost(), account.getPort(), account.getUsername(), account.getPassword());
        }
        return store;
    }
}
