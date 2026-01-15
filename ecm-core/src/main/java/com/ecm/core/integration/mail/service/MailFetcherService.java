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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Collectors;

/**
 * Service to fetch emails via IMAP and process them according to rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailFetcherService {

    private final MailAccountRepository accountRepository;
    private final MailRuleRepository ruleRepository;
    private final ProcessedMailRepository processedMailRepository;
    private final DocumentUploadService uploadService;
    private final TagService tagService;
    private final EmailIngestionService emailIngestionService;

    // Run every 5 minutes
    @Scheduled(fixedDelay = 300000)
    public void fetchAllAccounts() {
        List<MailAccount> accounts = accountRepository.findByEnabledTrue();
        log.info("Starting mail fetch for {} accounts", accounts.size());
        
        for (MailAccount account : accounts) {
            try {
                processAccount(account);
            } catch (Exception e) {
                log.error("Failed to process mail account: {}", account.getName(), e);
            }
        }
    }

    private void processAccount(MailAccount account) throws Exception {
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
            processFolder(store, account, entry.getKey(), entry.getValue());
        }

        store.close();
    }

    private void processFolder(Store store, MailAccount account, String folderName, List<MailRule> rules)
            throws Exception {
        Folder folder = store.getFolder(folderName);
        if (!folder.exists()) {
            log.warn("Mail folder '{}' does not exist for account {}", folderName, account.getName());
            return;
        }

        folder.open(Folder.READ_WRITE);

        Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        log.info("Found {} unread messages in {} ({})", messages.length, account.getName(), folderName);

        boolean expungeNeeded = false;
        for (Message message : messages) {
            try {
                expungeNeeded |= processMessage(message, rules, account, folderName, folder);
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
            Folder folder) throws Exception {
        String uid = resolveMessageUid(folder, message);
        if (processedMailRepository.existsByAccountIdAndFolderAndUid(account.getId(), folderName, uid)) {
            log.debug("Skipping already processed mail UID {} in {}", uid, folderName);
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
            return false;
        }

        log.info("Email matched rule: {}", matchedRule.getName());

        boolean processed = false;
        try {
            processed = processContent(message, matchedRule, attachments);
            if (!processed) {
                log.debug("Rule matched but no content processed for UID {}", uid);
                return false;
            }

            boolean expungeNeeded = applyMailAction(matchedRule, folder, message);
            recordProcessedMail(account, folderName, uid, matchedRule, message, ProcessedMail.Status.PROCESSED, null);
            return expungeNeeded;
        } catch (Exception e) {
            recordProcessedMail(account, folderName, uid, matchedRule, message, ProcessedMail.Status.ERROR,
                e.getMessage());
            throw e;
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

    private String buildEmailFilename(String subject) {
        String base = subject != null && !subject.isBlank() ? subject : "email";
        String sanitized = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        return sanitized + ".eml";
    }

    private record AttachmentPart(BodyPart part, String fileName, boolean inline) {
    }

    private Store connect(MailAccount account) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imap");
        
        if (account.getSecurity() == MailAccount.SecurityType.SSL) {
            props.put("mail.imap.ssl.enable", "true");
        } else if (account.getSecurity() == MailAccount.SecurityType.STARTTLS) {
            props.put("mail.imap.starttls.enable", "true");
        }

        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect(account.getHost(), account.getPort(), account.getUsername(), account.getPassword());
        return store;
    }
}
