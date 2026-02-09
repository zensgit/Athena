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
import com.ecm.core.repository.NodeRepository;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private NodeRepository nodeRepository;

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
            nodeRepository,
            uploadService,
            nodeService,
            tagService,
            emailIngestionService,
            meterRegistry,
            environment
        );
    }

    @Test
    @DisplayName("Runtime metrics include top error reasons")
    void runtimeMetricsIncludeTopErrors() {
        LocalDateTime now = LocalDateTime.now();
        MailAccount success = new MailAccount();
        success.setId(UUID.randomUUID());
        success.setLastFetchAt(now.minusMinutes(5));
        success.setLastFetchStatus("SUCCESS");

        MailAccount error = new MailAccount();
        error.setId(UUID.randomUUID());
        error.setLastFetchAt(now.minusMinutes(2));
        error.setLastFetchStatus("ERROR");

        when(accountRepository.findAll()).thenReturn(List.of(success, error));
        when(processedMailRepository.aggregateByAccount(any(LocalDateTime.class), any(LocalDateTime.class), eq(null)))
            .thenReturn(List.of(new RuntimeAccountAggregate(success.getId(), 8L, 2L, now.minusMinutes(2), now.minusMinutes(1))));
        when(processedMailRepository.aggregateTopErrors(any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(0, 5))))
            .thenReturn(List.of(
                new RuntimeErrorAggregate("Authentication failed for user admin@example.com", 3L, now.minusMinutes(2)),
                new RuntimeErrorAggregate("Folder not found: ECM-TEST", 1L, now.minusMinutes(1))
            ));

        var metrics = service.getRuntimeMetrics(60);

        assertEquals(2, metrics.attempts());
        assertEquals(1, metrics.successes());
        assertEquals(1, metrics.errors());
        assertEquals("DEGRADED", metrics.status());
        assertEquals(2, metrics.topErrors().size());
        assertEquals("Authentication failed for user admin@example.com", metrics.topErrors().get(0).errorMessage());
        assertEquals(3L, metrics.topErrors().get(0).count());
        assertEquals("STABLE", metrics.trend().direction());
        assertTrue(metrics.trend().summary().contains("unchanged") || metrics.trend().summary().contains("No processed"));
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
        when(processedMailRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(processed)));
        when(documentRepository.findRecentMailDocumentsWithFilters(25, accountId.toString(), ruleId.toString()))
            .thenReturn(List.of(document));

        var result = service.getDiagnostics(null, accountId, ruleId);

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
        when(processedMailRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(documentRepository.findRecentMailDocuments(200)).thenReturn(List.of());

        var result = service.getDiagnostics(999, null, null);

        assertEquals(200, result.limit());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(processedMailRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(200, pageableCaptor.getValue().getPageSize());
        verify(documentRepository).findRecentMailDocuments(200);
    }

    @Test
    @DisplayName("Export CSV respects selected fields")
    void exportCsvRespectsSelectedFields() {
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
        when(processedMailRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(processed)));
        when(documentRepository.findRecentMailDocumentsWithFilters(10, accountId.toString(), ruleId.toString()))
            .thenReturn(List.of(document));

        String csv = service.exportDiagnosticsCsv(
            10,
            accountId,
            ruleId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "req-test",
            "admin",
            true,
            true,
            false,
            false,
            false,
            true,
            false
        );

        String processedHeader = csv.lines()
            .filter((line) -> line.startsWith("ProcessedAt,"))
            .findFirst()
            .orElse("");
        assertTrue(processedHeader.contains("ProcessedAt"));
        assertTrue(!processedHeader.contains("Subject"));
        assertTrue(!processedHeader.contains("Error"));

        String docHeader = csv.lines()
            .filter((line) -> line.startsWith("CreatedAt,"))
            .findFirst()
            .orElse("");
        assertTrue(docHeader.contains("CreatedAt"));
        assertTrue(!docHeader.contains("Path"));
        assertTrue(docHeader.contains("MimeType"));
        assertTrue(!docHeader.contains("FileSize"));
    }

    @Test
    @DisplayName("Processed mail documents lists correlated ingested documents")
    void listProcessedMailDocumentsListsCorrelatedDocuments() {
        UUID accountId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID processedId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 1, 28, 10, 15);

        MailAccount account = new MailAccount();
        account.setId(accountId);
        account.setName("gmail-imap");

        MailRule rule = new MailRule();
        rule.setId(ruleId);
        rule.setName("gmail-attachments");

        ProcessedMail processed = new ProcessedMail();
        processed.setId(processedId);
        processed.setAccountId(accountId);
        processed.setRuleId(ruleId);
        processed.setFolder("INBOX");
        processed.setUid("12345");
        processed.setSubject("subject");
        processed.setProcessedAt(now);
        processed.setStatus(ProcessedMail.Status.PROCESSED);

        Document document1 = new Document();
        document1.setId(UUID.randomUUID());
        document1.setName("mail-attachment-1.pdf");
        document1.setPath("/Root/Documents/mail-attachment-1.pdf");
        document1.setCreatedDate(now);
        document1.setCreatedBy("admin");
        document1.setMimeType("application/pdf");
        document1.setFileSize(1024L);
        document1.getProperties().put("mail:accountId", accountId.toString());
        document1.getProperties().put("mail:ruleId", ruleId.toString());
        document1.getProperties().put("mail:folder", "INBOX");
        document1.getProperties().put("mail:uid", "12345");

        Document document2 = new Document();
        document2.setId(UUID.randomUUID());
        document2.setName("mail-attachment-2.pdf");
        document2.setPath("/Root/Documents/mail-attachment-2.pdf");
        document2.setCreatedDate(now.plusMinutes(1));
        document2.setCreatedBy("admin");
        document2.setMimeType("application/pdf");
        document2.setFileSize(2048L);
        document2.getProperties().put("mail:accountId", accountId.toString());
        document2.getProperties().put("mail:ruleId", ruleId.toString());
        document2.getProperties().put("mail:folder", "INBOX");
        document2.getProperties().put("mail:uid", "12345");

        when(processedMailRepository.findById(processedId)).thenReturn(Optional.of(processed));
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(ruleRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(rule));
        when(documentRepository.findMailDocumentsForMessage(200, accountId.toString(), "INBOX", "12345"))
            .thenReturn(List.of(document1, document2));

        var docs = service.listProcessedMailDocuments(processedId, 999);

        verify(documentRepository).findMailDocumentsForMessage(200, accountId.toString(), "INBOX", "12345");
        assertEquals(2, docs.size());
        assertEquals("mail-attachment-1.pdf", docs.get(0).name());
        assertEquals("gmail-imap", docs.get(0).accountName());
        assertEquals("gmail-attachments", docs.get(0).ruleName());
        assertEquals("INBOX", docs.get(0).folder());
        assertEquals("12345", docs.get(0).uid());
    }

    private static final class RuntimeErrorAggregate implements ProcessedMailRepository.MailRuntimeErrorAggregateRow {
        private final String errorMessage;
        private final Long totalCount;
        private final LocalDateTime lastSeenAt;

        private RuntimeErrorAggregate(String errorMessage, Long totalCount, LocalDateTime lastSeenAt) {
            this.errorMessage = errorMessage;
            this.totalCount = totalCount;
            this.lastSeenAt = lastSeenAt;
        }

        @Override
        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public Long getTotalCount() {
            return totalCount;
        }

        @Override
        public LocalDateTime getLastSeenAt() {
            return lastSeenAt;
        }
    }

    private static final class RuntimeAccountAggregate implements ProcessedMailRepository.MailAccountAggregateRow {
        private final UUID accountId;
        private final Long processedCount;
        private final Long errorCount;
        private final LocalDateTime lastProcessedAt;
        private final LocalDateTime lastErrorAt;

        private RuntimeAccountAggregate(
            UUID accountId,
            Long processedCount,
            Long errorCount,
            LocalDateTime lastProcessedAt,
            LocalDateTime lastErrorAt
        ) {
            this.accountId = accountId;
            this.processedCount = processedCount;
            this.errorCount = errorCount;
            this.lastProcessedAt = lastProcessedAt;
            this.lastErrorAt = lastErrorAt;
        }

        @Override
        public UUID getAccountId() {
            return accountId;
        }

        @Override
        public Long getProcessedCount() {
            return processedCount;
        }

        @Override
        public Long getErrorCount() {
            return errorCount;
        }

        @Override
        public LocalDateTime getLastProcessedAt() {
            return lastProcessedAt;
        }

        @Override
        public LocalDateTime getLastErrorAt() {
            return lastErrorAt;
        }
    }
}
