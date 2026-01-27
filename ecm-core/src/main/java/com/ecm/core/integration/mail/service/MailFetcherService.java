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
import org.springframework.core.env.Environment;
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
import java.util.HashMap;
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
    private static final int DEFAULT_DEBUG_MAX_MESSAGES_PER_FOLDER = 200;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String OAUTH_ENV_PREFIX = "ECM_MAIL_OAUTH_";

    private final MailAccountRepository accountRepository;
    private final MailRuleRepository ruleRepository;
    private final ProcessedMailRepository processedMailRepository;
    private final DocumentUploadService uploadService;
    private final TagService tagService;
    private final EmailIngestionService emailIngestionService;
    private final MeterRegistry meterRegistry;
    private final Environment environment;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ecm.mail.fetcher.run-as-user:admin}")
    private String runAsUser;

    @Value("${ecm.mail.fetcher.debug.max-messages-per-folder:200}")
    private int debugMaxMessagesPerFolder;

    private final Map<UUID, Instant> lastPollByAccount = new ConcurrentHashMap<>();
    private final Map<UUID, OAuthSession> oauthSessionByAccount = new ConcurrentHashMap<>();

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
                updateAccountFetchStatus(account, "SUCCESS", null);
            } catch (Exception e) {
                status = "error";
                reason = "exception";
                stats.accountErrors++;
                log.error("Failed to process mail account: {}", account.getName(), e);
                updateAccountFetchStatus(account, "ERROR", e.getMessage());
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

    public MailFetchDebugResult fetchAllAccountsDebug(boolean force, Integer maxMessagesPerFolder) {
        List<MailAccount> accounts = accountRepository.findByEnabledTrue();
        int effectiveMaxMessages = resolveDebugMaxMessages(maxMessagesPerFolder);
        log.info(
            "Starting mail fetch debug run for {} accounts (force={}, maxMessagesPerFolder={})",
            accounts.size(),
            force,
            effectiveMaxMessages
        );

        MailFetchDebugRunStats stats = new MailFetchDebugRunStats(effectiveMaxMessages);
        stats.accounts = accounts.size();
        long startNs = System.nanoTime();

        for (MailAccount account : accounts) {
            Instant now = Instant.now();
            List<MailRule> rules = findRulesForAccount(account);

            if (!force && !shouldProcessAccount(account, now)) {
                stats.skippedAccounts++;
                MailFetchDebugAccountResult result = new MailFetchDebugAccountResult(
                    account.getId(),
                    account.getName(),
                    false,
                    "poll_interval",
                    null,
                    rules.size(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    Map.of("poll_interval", 1),
                    Map.of(),
                    List.of()
                );
                stats.addAccountResult(result);
                continue;
            }

            stats.attemptedAccounts++;
            try {
                MailFetchDebugAccountResult result = runWithSystemAuthenticationWithResult(
                    () -> processAccountDebug(account, rules, effectiveMaxMessages)
                );
                stats.addAccountResult(result);
            } catch (Exception e) {
                stats.accountErrors++;
                MailFetchDebugAccountResult result = new MailFetchDebugAccountResult(
                    account.getId(),
                    account.getName(),
                    true,
                    "account_error",
                    truncateError(e.getMessage()),
                    rules.size(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    Map.of("account_error", 1),
                    Map.of(),
                    List.of()
                );
                stats.addAccountResult(result);
                log.warn("Mail debug run failed for account {}: {}", account.getName(), e.getMessage());
            }
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        MailFetchSummary summary = stats.toSummary(durationMs);
        log.info(
            "Mail fetch debug run completed: accounts={}, attempted={}, skipped={}, errors={}",
            summary.accounts(),
            summary.attemptedAccounts(),
            summary.skippedAccounts(),
            summary.accountErrors()
        );
        String topReasons = formatTopCounts(stats.skipReasons, 6);
        if (!topReasons.isBlank()) {
            log.info("Mail fetch debug top skip reasons: {}", topReasons);
        }
        return new MailFetchDebugResult(summary, effectiveMaxMessages, stats.skipReasons, stats.accountResults);
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
            .flatMap(rule -> normalizeFolders(rule.getFolder()).stream().map(folder -> Map.entry(folder, rule)))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                )
            );

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

    public record MailFetchDebugFolderResult(
        String folder,
        int rules,
        int foundMessages,
        int scannedMessages,
        int matchedMessages,
        int processableMessages,
        int skippedMessages,
        int errorMessages,
        Map<String, Integer> skipReasons
    ) {
    }

    public record MailFetchDebugAccountResult(
        UUID accountId,
        String accountName,
        boolean attempted,
        String skipReason,
        String accountError,
        int rules,
        int folders,
        int foundMessages,
        int scannedMessages,
        int matchedMessages,
        int processableMessages,
        int skippedMessages,
        int errorMessages,
        Map<String, Integer> skipReasons,
        Map<String, Integer> ruleMatches,
        List<MailFetchDebugFolderResult> folderResults
    ) {
    }

    public record MailFetchDebugResult(
        MailFetchSummary summary,
        int maxMessagesPerFolder,
        Map<String, Integer> skipReasons,
        List<MailFetchDebugAccountResult> accounts
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

    private static final class MailFetchDebugRunStats {
        private final int maxMessagesPerFolder;
        private int accounts;
        private int attemptedAccounts;
        private int skippedAccounts;
        private int accountErrors;
        private int foundMessages;
        private int matchedMessages;
        private int processedMessages;
        private int skippedMessages;
        private int errorMessages;
        private final Map<String, Integer> skipReasons = new HashMap<>();
        private final List<MailFetchDebugAccountResult> accountResults = new ArrayList<>();

        private MailFetchDebugRunStats(int maxMessagesPerFolder) {
            this.maxMessagesPerFolder = maxMessagesPerFolder;
        }

        private void addAccountResult(MailFetchDebugAccountResult result) {
            accountResults.add(result);
            foundMessages += result.foundMessages();
            matchedMessages += result.matchedMessages();
            processedMessages += result.processableMessages();
            skippedMessages += result.skippedMessages();
            errorMessages += result.errorMessages();
            mergeReasons(result.skipReasons());
        }

        private void mergeReasons(Map<String, Integer> reasons) {
            reasons.forEach(this::incrementSkipReason);
        }

        private void incrementSkipReason(String reason, int amount) {
            if (amount <= 0) {
                return;
            }
            skipReasons.merge(reason, amount, Integer::sum);
        }

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

    private static final class MessageDebugResult {
        private final boolean matched;
        private final boolean processable;
        private final boolean error;
        private final String reason;

        private MessageDebugResult(boolean matched, boolean processable, boolean error, String reason) {
            this.matched = matched;
            this.processable = processable;
            this.error = error;
            this.reason = reason;
        }
    }

    private int resolveDebugMaxMessages(Integer requested) {
        int configured = debugMaxMessagesPerFolder > 0 ? debugMaxMessagesPerFolder : DEFAULT_DEBUG_MAX_MESSAGES_PER_FOLDER;
        if (requested == null || requested <= 0) {
            return configured;
        }
        return requested;
    }

    private List<MailRule> findRulesForAccount(MailAccount account) {
        return ruleRepository.findAllByOrderByPriorityAsc().stream()
            .filter(rule -> rule.getAccountId() == null || Objects.equals(rule.getAccountId(), account.getId()))
            .collect(Collectors.toList());
    }

    private MailFetchDebugAccountResult processAccountDebug(
        MailAccount account,
        List<MailRule> rules,
        int maxMessagesPerFolder
    ) throws Exception {
        if (rules.isEmpty()) {
            return new MailFetchDebugAccountResult(
                account.getId(),
                account.getName(),
                true,
                "no_rules",
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of("no_rules", 1),
                Map.of(),
                List.of()
            );
        }

        Map<String, List<MailRule>> rulesByFolder = rules.stream()
            .flatMap(rule -> normalizeFolders(rule.getFolder()).stream().map(folder -> Map.entry(folder, rule)))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                )
            );

        Store store = null;
        try {
            store = connect(account);
            Map<String, Integer> skipReasons = new HashMap<>();
            Map<String, Integer> ruleMatches = new HashMap<>();
            List<MailFetchDebugFolderResult> folderResults = new ArrayList<>();
            int foundMessages = 0;
            int scannedMessages = 0;
            int matchedMessages = 0;
            int processableMessages = 0;
            int skippedMessages = 0;
            int errorMessages = 0;

            for (Map.Entry<String, List<MailRule>> entry : rulesByFolder.entrySet()) {
                MailFetchDebugFolderResult folderResult = processFolderDebug(
                    store,
                    account,
                    entry.getKey(),
                    entry.getValue(),
                    maxMessagesPerFolder,
                    ruleMatches
                );
                folderResults.add(folderResult);
                foundMessages += folderResult.foundMessages();
                scannedMessages += folderResult.scannedMessages();
                matchedMessages += folderResult.matchedMessages();
                processableMessages += folderResult.processableMessages();
                skippedMessages += folderResult.skippedMessages();
                errorMessages += folderResult.errorMessages();
                folderResult.skipReasons().forEach((reason, count) -> incrementReason(skipReasons, reason, count));
            }

            log.info(
                "Mail debug summary for {}: found={}, matched={}, processable={}, skipped={}, errors={}, topSkips=[{}], topRules=[{}]",
                account.getName(),
                foundMessages,
                matchedMessages,
                processableMessages,
                skippedMessages,
                errorMessages,
                formatTopCounts(skipReasons, 5),
                formatTopCounts(ruleMatches, 5)
            );
            return new MailFetchDebugAccountResult(
                account.getId(),
                account.getName(),
                true,
                null,
                null,
                rules.size(),
                rulesByFolder.size(),
                foundMessages,
                scannedMessages,
                matchedMessages,
                processableMessages,
                skippedMessages,
                errorMessages,
                skipReasons,
                ruleMatches,
                folderResults
            );
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (Exception ignored) {
                    // best effort for debug runs
                }
            }
        }
    }

    private MailFetchDebugFolderResult processFolderDebug(
        Store store,
        MailAccount account,
        String folderName,
        List<MailRule> rules,
        int maxMessagesPerFolder,
        Map<String, Integer> ruleMatches
    ) {
        Map<String, Integer> skipReasons = new HashMap<>();
        Folder folder = null;
        try {
            folder = store.getFolder(folderName);
            if (!folder.exists()) {
                incrementReason(skipReasons, "folder_missing", 1);
                log.warn("Mail folder '{}' does not exist for account {} (debug run)", folderName, account.getName());
                return new MailFetchDebugFolderResult(
                    folderName,
                    rules.size(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    skipReasons
                );
            }

            folder.open(Folder.READ_ONLY);
            Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            int foundMessages = messages.length;
            int scannedMessages = foundMessages;
            int notScanned = 0;
            if (maxMessagesPerFolder > 0 && foundMessages > maxMessagesPerFolder) {
                scannedMessages = maxMessagesPerFolder;
                notScanned = foundMessages - scannedMessages;
                incrementReason(skipReasons, "limit_not_scanned", notScanned);
            }

            int matchedMessages = 0;
            int processableMessages = 0;
            int skippedMessages = 0;
            int errorMessages = 0;
            if (notScanned > 0) {
                skippedMessages += notScanned;
            }

            for (int i = 0; i < scannedMessages; i++) {
                Message message = messages[i];
                MessageDebugResult result = processMessageDebug(message, rules, account, folderName, folder, ruleMatches);
                if (result.error) {
                    errorMessages++;
                    incrementReason(skipReasons, result.reason, 1);
                    continue;
                }
                if (result.matched) {
                    matchedMessages++;
                }
                if (result.processable) {
                    processableMessages++;
                    continue;
                }
                skippedMessages++;
                incrementReason(skipReasons, result.reason, 1);
            }

            log.info(
                "Mail debug folder {}:{} found={}, scanned={}, matched={}, processable={}, skipped={}, errors={}",
                account.getName(),
                folderName,
                foundMessages,
                scannedMessages,
                matchedMessages,
                processableMessages,
                skippedMessages,
                errorMessages
            );
            return new MailFetchDebugFolderResult(
                folderName,
                rules.size(),
                foundMessages,
                scannedMessages,
                matchedMessages,
                processableMessages,
                skippedMessages,
                errorMessages,
                skipReasons
            );
        } catch (Exception e) {
            incrementReason(skipReasons, "folder_error", 1);
            log.warn(
                "Mail debug run failed for account {} folder {}: {}",
                account.getName(),
                folderName,
                e.getMessage()
            );
            return new MailFetchDebugFolderResult(
                folderName,
                rules.size(),
                0,
                0,
                0,
                0,
                0,
                1,
                skipReasons
            );
        } finally {
            if (folder != null && folder.isOpen()) {
                try {
                    folder.close(false);
                } catch (Exception ignored) {
                    // best effort for debug runs
                }
            }
        }
    }

    private MessageDebugResult processMessageDebug(
        Message message,
        List<MailRule> rules,
        MailAccount account,
        String folderName,
        Folder folder,
        Map<String, Integer> ruleMatches
    ) {
        try {
            String uid = resolveMessageUid(folder, message);
            if (processedMailRepository.existsByAccountIdAndFolderAndUid(account.getId(), folderName, uid)) {
                return new MessageDebugResult(false, false, false, "already_processed");
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
                return new MessageDebugResult(false, false, false, "no_rule");
            }

            ruleMatches.merge(matchedRule.getName(), 1, Integer::sum);
            boolean processable = wouldProcessContent(matchedRule, attachments);
            if (!processable) {
                return new MessageDebugResult(true, false, false, "no_content");
            }

            return new MessageDebugResult(true, true, false, "processable");
        } catch (Exception e) {
            log.debug("Mail debug message processing failed: {}", e.getMessage());
            return new MessageDebugResult(false, false, true, "message_error");
        }
    }

    private boolean wouldProcessContent(MailRule rule, List<AttachmentPart> attachments) {
        MailRule.MailActionType actionType = rule.getActionType() != null
            ? rule.getActionType()
            : MailRule.MailActionType.ATTACHMENTS_ONLY;

        if (actionType == MailRule.MailActionType.METADATA_ONLY
            || actionType == MailRule.MailActionType.EVERYTHING) {
            return true;
        }

        return countEligibleAttachments(rule, attachments) > 0;
    }

    private int countEligibleAttachments(MailRule rule, List<AttachmentPart> attachments) {
        boolean includeInline = Boolean.TRUE.equals(rule.getIncludeInlineAttachments());
        int eligible = 0;
        for (AttachmentPart attachment : attachments) {
            if (!includeInline && attachment.inline()) {
                continue;
            }
            if (attachmentMatchesRule(attachment.fileName(), rule)) {
                eligible++;
            }
        }
        return eligible;
    }

    private static void incrementReason(Map<String, Integer> reasons, String reason, int amount) {
        if (amount <= 0) {
            return;
        }
        reasons.merge(reason, amount, Integer::sum);
    }

    private static String formatTopCounts(Map<String, Integer> counts, int limit) {
        if (counts == null || counts.isEmpty()) {
            return "";
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(Math.max(1, limit))
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(", "));
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

    private List<String> normalizeFolders(String folders) {
        if (folders == null || folders.isBlank()) {
            return List.of("INBOX");
        }
        List<String> normalized = List.of(folders.split(",")).stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            return List.of("INBOX");
        }
        return normalized;
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

    private void updateAccountFetchStatus(MailAccount account, String status, String errorMessage) {
        try {
            account.setLastFetchAt(LocalDateTime.now());
            account.setLastFetchStatus(status);
            account.setLastFetchError(truncateError(errorMessage));
            accountRepository.save(account);
        } catch (Exception e) {
            log.warn("Failed to update fetch status for account {}", account.getName(), e);
        }
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        String trimmed = errorMessage.trim();
        if (trimmed.length() <= MAX_ERROR_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_ERROR_LENGTH);
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

    private <T> T runWithSystemAuthenticationWithResult(MailFetcherSupplier<T> task) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return task.get();
        }

        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext systemContext = SecurityContextHolder.createEmptyContext();
        systemContext.setAuthentication(createSystemAuthentication());
        SecurityContextHolder.setContext(systemContext);
        try {
            return task.get();
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

    @FunctionalInterface
    private interface MailFetcherSupplier<T> {
        T get() throws Exception;
    }

    private String buildEmailFilename(String subject) {
        String base = subject != null && !subject.isBlank() ? subject : "email";
        String sanitized = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        return sanitized + ".eml";
    }

    private record AttachmentPart(BodyPart part, String fileName, boolean inline) {
    }

    private String resolveOAuthAccessToken(MailAccount account) {
        OAuthSession session = oauthSessionByAccount.get(account.getId());
        if (session != null && !isTokenExpired(session.expiresAt())) {
            return session.accessToken();
        }

        OAuthTokenResponse response = refreshOAuthAccessToken(account);
        LocalDateTime expiresAt = null;
        if (response.expiresIn() != null && response.expiresIn() > 0) {
            expiresAt = LocalDateTime.now().plusSeconds(response.expiresIn());
        }
        oauthSessionByAccount.put(account.getId(), new OAuthSession(response.accessToken(), expiresAt));
        return response.accessToken();
    }

    private OAuthTokenResponse refreshOAuthAccessToken(MailAccount account) {
        String clientId = resolveOAuthClientId(account);
        String refreshTokenEnv = resolveOAuthRefreshToken(account);
        String clientSecret = resolveOAuthClientSecret(account);
        String scope = resolveOAuthScope(account);
        String tokenEndpoint = resolveOAuthTokenEndpoint(account);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("refresh_token", refreshTokenEnv);
        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
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

        return new OAuthTokenResponse(accessToken, refreshToken, expiresIn);
    }

    private String resolveOAuthTokenEndpoint(MailAccount account) {
        String override = resolveOAuthEnv(account, "TOKEN_ENDPOINT", false);
        if (override != null && !override.isBlank()) {
            return override;
        }
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

    private record OAuthSession(String accessToken, LocalDateTime expiresAt) {
    }

    public record OAuthEnvCheckResult(
        boolean oauthAccount,
        boolean configured,
        String credentialKey,
        List<String> missingEnvKeys
    ) {
    }

    public OAuthEnvCheckResult checkOAuthEnv(UUID accountId) {
        MailAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Mail account not found: " + accountId));
        return checkOAuthEnv(account);
    }

    public OAuthEnvCheckResult checkOAuthEnv(MailAccount account) {
        if (account.getSecurity() != MailAccount.SecurityType.OAUTH2) {
            return new OAuthEnvCheckResult(false, true, null, List.of());
        }

        String normalizedKey = normalizeCredentialKeyNullable(account.getOauthCredentialKey());
        if (normalizedKey == null) {
            return new OAuthEnvCheckResult(true, false, null, List.of("oauthCredentialKey"));
        }

        List<String> requiredEnvKeys = List.of(
            buildOauthEnvKey(normalizedKey, "CLIENT_ID"),
            buildOauthEnvKey(normalizedKey, "REFRESH_TOKEN")
        );
        List<String> missing = requiredEnvKeys.stream()
            .filter(this::isMissingEnvValue)
            .toList();

        return new OAuthEnvCheckResult(true, missing.isEmpty(), normalizedKey, missing);
    }

    private String resolveOAuthCredentialKey(MailAccount account) {
        String normalizedKey = normalizeCredentialKeyNullable(account.getOauthCredentialKey());
        if (normalizedKey == null) {
            throw new IllegalStateException("OAuth credential key missing for account " + account.getName());
        }
        return normalizedKey;
    }

    private String normalizeCredentialKeyNullable(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

    private String buildOauthEnvKey(String normalizedKey, String suffix) {
        return OAUTH_ENV_PREFIX + normalizedKey + "_" + suffix;
    }

    private boolean isMissingEnvValue(String envKey) {
        String value = environment.getProperty(envKey);
        return value == null || value.isBlank();
    }

    private String resolveOAuthEnv(MailAccount account, String suffix, boolean required) {
        String normalizedKey = resolveOAuthCredentialKey(account);
        String envKey = buildOauthEnvKey(normalizedKey, suffix);
        String value = environment.getProperty(envKey);
        if (required && (value == null || value.isBlank())) {
            throw new IllegalStateException("Missing OAuth env var " + envKey + " for account " + account.getName());
        }
        return value;
    }

    private String resolveOAuthClientId(MailAccount account) {
        return resolveOAuthEnv(account, "CLIENT_ID", true);
    }

    private String resolveOAuthClientSecret(MailAccount account) {
        return resolveOAuthEnv(account, "CLIENT_SECRET", false);
    }

    private String resolveOAuthRefreshToken(MailAccount account) {
        return resolveOAuthEnv(account, "REFRESH_TOKEN", true);
    }

    private String resolveOAuthScope(MailAccount account) {
        String scope = resolveOAuthEnv(account, "SCOPE", false);
        if (scope != null && !scope.isBlank()) {
            return scope;
        }
        return account.getOauthScope();
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
