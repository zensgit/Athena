package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.model.MailRule;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.TagService;
import jakarta.mail.*;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.io.IOUtils;

import java.util.Properties;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service to fetch emails via IMAP and process them according to rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailFetcherService {

    private final MailAccountRepository accountRepository;
    private final MailRuleRepository ruleRepository;
    private final DocumentUploadService uploadService;
    private final TagService tagService;

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
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        // Fetch unread messages
        Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        log.info("Found {} unread messages in {}", messages.length, account.getName());

        List<MailRule> rules = ruleRepository.findAllByOrderByPriorityAsc();

        for (Message message : messages) {
            try {
                processMessage(message, rules, account);
                message.setFlag(Flags.Flag.SEEN, true); // Mark as read
            } catch (Exception e) {
                log.error("Error processing message: {}", message.getSubject(), e);
            }
        }

        inbox.close(false);
        store.close();
    }

    private void processMessage(Message message, List<MailRule> rules, MailAccount account) throws Exception {
        String subject = message.getSubject();
        String from = message.getFrom()[0].toString();
        log.debug("Processing email: Subject='{}', From='{}'", subject, from);

        // 1. Match Rules
        MailRule matchedRule = null;
        for (MailRule rule : rules) {
            if (rule.getAccountId() != null && !rule.getAccountId().equals(account.getId())) {
                continue;
            }
            if (matches(rule, subject, from)) {
                matchedRule = rule;
                break;
            }
        }

        if (matchedRule == null) {
            log.debug("No rule matched for email, skipping ingestion.");
            return;
        }

        log.info("Email matched rule: {}", matchedRule.getName());

        // 2. Process Content (Attachments)
        if (message.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) message.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) || 
                    (bodyPart.getFileName() != null && !bodyPart.getFileName().isEmpty())) {
                    
                    ingestAttachment(bodyPart, matchedRule);
                }
            }
        }
    }

    private void ingestAttachment(BodyPart part, MailRule rule) throws Exception {
        String fileName = part.getFileName();
        byte[] content = IOUtils.toByteArray(part.getInputStream());
        
        MultipartFile file = new MockMultipartFile(fileName, fileName, part.getContentType(), content);
        
        // Upload
        var result = uploadService.uploadDocument(file, rule.getAssignFolderId(), null);
        
        // Apply Tags
        if (result.isSuccess() && rule.getAssignTagId() != null) {
            tagService.addTagToNodeById(result.getDocumentId().toString(), rule.getAssignTagId());
            log.info("Applied tag {} to document {}", rule.getAssignTagId(), result.getDocumentId());
        }
    }

    private boolean matches(MailRule rule, String subject, String from) {
        if (rule.getSubjectFilter() != null && !rule.getSubjectFilter().isEmpty()) {
            if (!Pattern.compile(rule.getSubjectFilter(), Pattern.CASE_INSENSITIVE).matcher(subject).find()) {
                return false;
            }
        }
        if (rule.getFromFilter() != null && !rule.getFromFilter().isEmpty()) {
            if (!Pattern.compile(rule.getFromFilter(), Pattern.CASE_INSENSITIVE).matcher(from).find()) {
                return false;
            }
        }
        return true;
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
