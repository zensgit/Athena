package com.ecm.core.integration.mail.service;

import com.ecm.core.entity.Document;
import com.ecm.core.integration.email.EmailIngestionService;
import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.model.MailRule;
import com.ecm.core.integration.mail.model.ProcessedMail;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.TagService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailFetcherServiceDiagnosticsTest {

    @Mock
    private MailAccountRepository accountRepository;

    @Mock
    private MailRuleRepository ruleRepository;

    @Mock
    private ProcessedMailRepository processedMailRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentUploadService uploadService;

    @Mock
    private NodeService nodeService;

    @Mock
    private TagService tagService;

    @Mock
    private EmailIngestionService emailIngestionService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Environment environment;

    private MailFetcherService service;

    @BeforeEach
    void setUp() {
        service = new MailFetcherService(
            accountRepository,
            ruleRepository,
            processedMailRepository,
            documentRepository,
            uploadService,
            nodeService,
            tagService,
            emailIngestionService,
            meterRegistry,
            environment
        );
    }

    @Test
    @DisplayName("Diagnostics maps processed mail and document metadata")
    void diagnosticsMapsRecentItems() {
        UUID accountId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 1, 28, 10, 15);

        MailAccount account = new MailAccount();
        account.setId(accountId);
        account.setName("gmail-imap");

        MailRule rule = new MailRule();
        rule.setId(ruleId);
        rule.setName("gmail-attachments");

        ProcessedMail processed = new ProcessedMail();
        processed.setId(UUID.randomUUID());
        processed.setAccountId(accountId);
        processed.setRuleId(ruleId);
        processed.setFolder("INBOX");
        processed.setUid("12345");
        processed.setSubject("subject");
        processed.setProcessedAt(now);
        processed.setStatus(ProcessedMail.Status.PROCESSED);

        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("mail-attachment.pdf");
        document.setPath("/Root/Documents/mail-attachment.pdf");
        document.setCreatedDate(now);
        document.setCreatedBy("admin");
        document.setMimeType("application/pdf");
        document.setFileSize(1024L);
        document.getProperties().put("mail:accountId", accountId.toString());
        document.getProperties().put("mail:ruleId", ruleId.toString());
        document.getProperties().put("mail:folder", "INBOX");
        document.getProperties().put("mail:uid", "12345");

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(ruleRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(rule));
        when(processedMailRepository.findAllByOrderByProcessedAtDesc(any(Pageable.class)))
            .thenReturn(List.of(processed));
        when(documentRepository.findRecentMailDocuments(25)).thenReturn(List.of(document));

        var result = service.getDiagnostics(null);

        assertEquals(25, result.limit());
        assertEquals(1, result.recentProcessed().size());
        assertEquals("gmail-imap", result.recentProcessed().get(0).accountName());
        assertEquals("gmail-attachments", result.recentProcessed().get(0).ruleName());
        assertEquals(1, result.recentDocuments().size());
        assertEquals("gmail-imap", result.recentDocuments().get(0).accountName());
        assertEquals("gmail-attachments", result.recentDocuments().get(0).ruleName());
    }

    @Test
    @DisplayName("Diagnostics clamps limit to configured bounds")
    void diagnosticsClampsLimit() {
        when(accountRepository.findAll()).thenReturn(List.of());
        when(ruleRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of());
        when(processedMailRepository.findAllByOrderByProcessedAtDesc(any(Pageable.class)))
            .thenReturn(List.of());
        when(documentRepository.findRecentMailDocuments(200)).thenReturn(List.of());

        var result = service.getDiagnostics(999);

        assertEquals(200, result.limit());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(processedMailRepository).findAllByOrderByProcessedAtDesc(pageableCaptor.capture());
        assertEquals(200, pageableCaptor.getValue().getPageSize());
        verify(documentRepository).findRecentMailDocuments(200);
    }
}
