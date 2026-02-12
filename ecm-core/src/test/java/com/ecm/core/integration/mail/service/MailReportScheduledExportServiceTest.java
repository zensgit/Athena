package com.ecm.core.integration.mail.service;

import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.DocumentUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Test
    @DisplayName("Export is skipped when schedule is disabled")
    void exportSkippedWhenDisabled() {
        MailReportScheduledExportService service = new MailReportScheduledExportService(
            reportingService,
            uploadService,
            auditService
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
            auditService
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
            auditService
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
