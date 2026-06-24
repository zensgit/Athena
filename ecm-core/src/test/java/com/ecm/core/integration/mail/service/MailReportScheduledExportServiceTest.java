package com.ecm.core.integration.mail.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ecm.core.config.TenantContext;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.TenantContextResolverService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailReportScheduledExportServiceTest {

    @Mock
    private MailReportingService reportingService;

    @Mock
    private DocumentUploadService uploadService;

    @Mock
    private AuditService auditService;

    @Mock
    private TenantContextResolverService tenantContextResolverService;

    @Test
    @DisplayName("Export is skipped when schedule is disabled")
    void exportSkippedWhenDisabled() {
        MailReportScheduledExportService service = new MailReportScheduledExportService(
            reportingService,
            uploadService,
            auditService,
            tenantContextResolverService
        );
        setField(service, "enabled", false);

        MailReportScheduledExportService.ScheduledExportResult result = service.exportNow(true);

        assertFalse(result.attempted());
        assertTrue(result.success());
        assertEquals("SKIPPED", result.status());
        verifyNoInteractions(reportingService, uploadService, auditService);
    }

    @Test
    @DisplayName("Export is skipped when folder id is missing/invalid")
    void exportSkippedWhenFolderMissing() {
        MailReportScheduledExportService service = new MailReportScheduledExportService(
            reportingService,
            uploadService,
            auditService,
            tenantContextResolverService
        );
        setField(service, "enabled", true);
        setField(service, "folderIdRaw", "");

        MailReportScheduledExportService.ScheduledExportResult result = service.exportNow(true);

        assertFalse(result.attempted());
        assertTrue(result.success());
        assertEquals("SKIPPED", result.status());
        verifyNoInteractions(reportingService, uploadService);
    }

    @Test
    @DisplayName("Export uploads CSV document to configured folder when enabled")
    void exportUploadsCsvWhenEnabled() throws Exception {
        MailReportScheduledExportService service = new MailReportScheduledExportService(
            reportingService,
            uploadService,
            auditService,
            tenantContextResolverService
        );
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        setField(service, "enabled", true);
        setField(service, "folderIdRaw", folderId.toString());
        setField(service, "days", 30);
        setField(service, "accountIdRaw", "");
        setField(service, "ruleIdRaw", "");

        LocalDate end = LocalDate.now();
        MailReportingService.MailReportResponse report = new MailReportingService.MailReportResponse(
            null,
            null,
            end.minusDays(29),
            end,
            30,
            new MailReportingService.MailReportTotals(1, 0, 1),
            List.of(),
            List.of(),
            List.of()
        );
        when(reportingService.getReport(isNull(), isNull(), isNull(), isNull(), eq(30))).thenReturn(report);
        when(reportingService.exportReportCsv(eq(report))).thenReturn("a,b\n1,2\n");
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenReturn(PipelineResult.builder().success(true).documentId(documentId).build());

        MailReportScheduledExportService.ScheduledExportResult result = service.exportNow(true);

        assertTrue(result.attempted());
        assertTrue(result.success());
        assertEquals("SUCCESS", result.status());
        assertEquals(folderId, result.folderId());
        assertEquals(documentId, result.documentId());
        assertNotNull(result.filename());
        assertTrue(result.filename().startsWith("mail-report-"));
        assertTrue(result.filename().endsWith(".csv"));
        assertTrue(result.durationMs() >= 0);
        assertEquals(30, result.days());

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(uploadService).uploadDocument(fileCaptor.capture(), eq(folderId), isNull());
        MultipartFile uploaded = fileCaptor.getValue();
        assertEquals("text/csv", uploaded.getContentType());
        assertEquals(result.filename(), uploaded.getOriginalFilename());
        assertEquals("a,b\n1,2\n", new String(uploaded.getBytes(), StandardCharsets.UTF_8));

        verify(auditService).logEvent(
            eq("MAIL_REPORT_SCHEDULED_EXPORTED"),
            eq(documentId),
            eq("MAIL_REPORT"),
            eq("admin"),
            contains("Scheduled mail report export")
        );

        // Ensure the read-only status endpoint can surface the last export
        MailReportScheduledExportService.MailReportScheduleStatus status = service.getScheduleStatus();
        assertTrue(status.enabled());
        assertEquals(folderId, status.folderId());
        assertNotNull(status.lastExport());
        assertEquals("SUCCESS", status.lastExport().status());
    }

    // ---- Q2b: the export write is scoped to the configured folder's tenant ----

    @BeforeEach
    void stubDefaultTenantResolution() {
        // exportNow now resolves a tenant before the write. Existing export tests don't care which
        // tenant, but an unstubbed resolver returns null → NPE inside the try → a misleading FAILED.
        // lenient: the disabled / missing-folder skip tests never reach resolve. The Q2b tests below
        // register a more specific stub for their own folderId, which takes precedence.
        lenient().when(tenantContextResolverService.resolveTenantForTargetFolder(any()))
            .thenReturn(TenantContextResolverService.TenantResolution.resolved("default-tenant", UUID.randomUUID()));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Q2b: export write runs under the configured folder's tenant and is restored after")
    void exportScopesWriteToFolderTenantAndRestoresAfter() throws Exception {
        MailReportScheduledExportService service = new MailReportScheduledExportService(
            reportingService, uploadService, auditService, tenantContextResolverService);
        UUID folderId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        setField(service, "enabled", true);
        setField(service, "folderIdRaw", folderId.toString());
        setField(service, "days", 30);
        setField(service, "accountIdRaw", "");
        setField(service, "ruleIdRaw", "");
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantContextResolverService.TenantResolution.resolved("acme", rootId));
        stubReportAndCsv();
        // The write must observe the resolved tenant AT upload time.
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenAnswer(inv -> {
                assertEquals("acme", TenantContext.getCurrentTenantDomain());
                assertEquals(rootId, TenantContext.getCurrentTenantRootNodeId());
                return PipelineResult.builder().success(true).documentId(UUID.randomUUID()).build();
            });

        MailReportScheduledExportService.ScheduledExportResult result = service.exportNow(true);

        assertEquals("SUCCESS", result.status());
        // This thread had no tenant on entry → restored to empty, no leak into the next scheduler tick.
        assertNull(TenantContext.getCurrentTenantDomain());
        assertNull(TenantContext.getCurrentTenantRootNodeId());
    }

    @Test
    @DisplayName("Q2b: folder configured but not under an enabled tenant -> FAILED (not skipped), no write")
    void exportFailsWhenFolderNotUnderTenant() throws Exception {
        MailReportScheduledExportService service = new MailReportScheduledExportService(
            reportingService, uploadService, auditService, tenantContextResolverService);
        UUID folderId = UUID.randomUUID();
        setField(service, "enabled", true);
        setField(service, "folderIdRaw", folderId.toString());
        setField(service, "days", 30);
        setField(service, "accountIdRaw", "");
        setField(service, "ruleIdRaw", "");
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantContextResolverService.TenantResolution.unresolved());

        MailReportScheduledExportService.ScheduledExportResult result = service.exportNow(true);

        // A configured-but-non-tenant folder is a config ERROR surfaced as FAILED — distinct from the
        // missing/invalid-id SKIPPED path. resolve is the first try statement, so getReport never runs.
        assertEquals("FAILED", result.status());
        assertTrue(result.attempted());
        assertFalse(result.success());
        verify(uploadService, never()).uploadDocument(any(), any(), any());
        verifyNoInteractions(reportingService);
        assertNull(TenantContext.getCurrentTenantDomain());
    }

    @Test
    @DisplayName("Q2b: upload failure stays FAILED and restores the caller's tenant (manual path)")
    void exportRestoresCallerTenantWhenUploadThrows() throws Exception {
        MailReportScheduledExportService service = new MailReportScheduledExportService(
            reportingService, uploadService, auditService, tenantContextResolverService);
        UUID folderId = UUID.randomUUID();
        setField(service, "enabled", true);
        setField(service, "folderIdRaw", folderId.toString());
        setField(service, "days", 30);
        setField(service, "accountIdRaw", "");
        setField(service, "ruleIdRaw", "");
        // Manual exportNow(true) invoked from a request thread that already carries its own tenant.
        TenantContext.setCurrentTenantDomain("caller-tenant");
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantContextResolverService.TenantResolution.resolved("acme", UUID.randomUUID()));
        stubReportAndCsv();
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenThrow(new java.io.IOException("boom"));

        MailReportScheduledExportService.ScheduledExportResult result = service.exportNow(true);

        // absorb-style: the existing catch maps the upload throw to a FAILED result (no rethrow).
        assertEquals("FAILED", result.status());
        // The caller's original tenant is restored — not cleared, not left as the resolved "acme".
        assertEquals("caller-tenant", TenantContext.getCurrentTenantDomain());
    }

    @Test
    @DisplayName("Phase 2 mail slice (:163/:165): a failed export records the exception TYPE only — the thrown message reaches neither the admin-UI result nor the log")
    void exportFailureRecordsTypeNotThrownMessage() throws Exception {
        MailReportScheduledExportService service = new MailReportScheduledExportService(
            reportingService, uploadService, auditService, tenantContextResolverService);
        UUID folderId = UUID.randomUUID();
        setField(service, "enabled", true);
        setField(service, "folderIdRaw", folderId.toString());
        setField(service, "days", 30);
        setField(service, "accountIdRaw", "");
        setField(service, "ruleIdRaw", "");
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantContextResolverService.TenantResolution.resolved("acme", UUID.randomUUID()));
        stubReportAndCsv();
        String sensitive = "smtp://user:p@ssw0rd@mail.example failed BODY-LEAK-zzz";
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenThrow(new java.io.UncheckedIOException(new java.io.IOException(sensitive)));

        Logger logger = (Logger) LoggerFactory.getLogger(MailReportScheduledExportService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MailReportScheduledExportService.ScheduledExportResult result = service.exportNow(true);

            assertEquals("FAILED", result.status());
            // the admin-UI result message is the exception TYPE, not the thrown (sensitive) message
            assertEquals("UncheckedIOException", result.message());
            assertFalse(result.message().contains("BODY-LEAK-zzz"));
            assertFalse(result.message().contains("p@ssw0rd"));

            ILoggingEvent event = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("scheduled export errored"))
                .findFirst().orElseThrow(() -> new AssertionError("expected the :165 export-error log"));
            assertNull(event.getThrowableProxy(), ":165 log must not carry the Throwable");
            assertFalse(event.getFormattedMessage().contains("BODY-LEAK-zzz"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private void stubReportAndCsv() {
        LocalDate end = LocalDate.now();
        MailReportingService.MailReportResponse report = new MailReportingService.MailReportResponse(
            null,
            null,
            end.minusDays(29),
            end,
            30,
            new MailReportingService.MailReportTotals(1, 0, 1),
            List.of(),
            List.of(),
            List.of()
        );
        when(reportingService.getReport(isNull(), isNull(), isNull(), isNull(), eq(30))).thenReturn(report);
        when(reportingService.exportReportCsv(eq(report))).thenReturn("a,b\n1,2\n");
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to set field: " + fieldName, ex);
        }
    }
}
