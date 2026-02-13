package com.ecm.core.integration.mail.service;

import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.DocumentUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailReportScheduledExportService {

    private static final String AUDIT_EVENT = "MAIL_REPORT_SCHEDULED_EXPORTED";

    private final MailReportingService reportingService;
    private final DocumentUploadService uploadService;
    private final AuditService auditService;

    @Value("${ecm.mail.reporting.export.enabled:false}")
    private boolean enabled;

    @Value("${ecm.mail.reporting.export.cron:0 5 2 * * *}")
    private String cron;

    @Value("${ecm.mail.reporting.export.folder-id:}")
    private String folderIdRaw;

    @Value("${ecm.mail.reporting.export.days:30}")
    private int days;

    @Value("${ecm.mail.reporting.export.account-id:}")
    private String accountIdRaw;

    @Value("${ecm.mail.reporting.export.rule-id:}")
    private String ruleIdRaw;

    private final AtomicReference<ScheduledExportResult> lastExport = new AtomicReference<>();

    public MailReportScheduleStatus getScheduleStatus() {
        return new MailReportScheduleStatus(
            enabled,
            cron,
            parseUuid(folderIdRaw),
            Math.max(1, days),
            parseUuid(accountIdRaw),
            parseUuid(ruleIdRaw),
            lastExport.get()
        );
    }

    @Scheduled(cron = "${ecm.mail.reporting.export.cron:0 5 2 * * *}")
    @Transactional
    public void scheduledExport() {
        exportNow(false);
    }

    @Transactional
    public ScheduledExportResult exportNow(boolean manual) {
        if (!enabled) {
            ScheduledExportResult result = ScheduledExportResult.skipped("Schedule disabled", manual);
            lastExport.set(result);
            return result;
        }

        UUID folderId = parseUuid(folderIdRaw);
        if (folderId == null) {
            ScheduledExportResult result = ScheduledExportResult.skipped("Missing/invalid export folder id", manual);
            lastExport.set(result);
            log.warn("Mail report scheduled export skipped: folder-id not configured or invalid");
            return result;
        }

        UUID accountId = parseUuid(accountIdRaw);
        UUID ruleId = parseUuid(ruleIdRaw);

        LocalDateTime startedAt = LocalDateTime.now();
        String filename = "mail-report-" + LocalDate.now() + ".csv";
        try {
            MailReportingService.MailReportResponse report = reportingService.getReport(accountId, ruleId, null, null, days);
            String csv = reportingService.exportReportCsv(report);

            MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
            );

            PipelineResult uploadResult = uploadService.uploadDocument(file, folderId, null);
            if (!uploadResult.isSuccess()) {
                String message = uploadResult.getErrors() != null ? uploadResult.getErrors().toString() : "unknown upload error";
                ScheduledExportResult result = ScheduledExportResult.failed(message, manual, filename, folderId, startedAt, days);
                lastExport.set(result);
                log.warn("Mail report scheduled export failed: {}", message);
                return result;
            }

            UUID documentId = uploadResult.getDocumentId();
            ScheduledExportResult result = ScheduledExportResult.success(manual, filename, folderId, documentId, startedAt, days);
            lastExport.set(result);
            auditService.logEvent(
                AUDIT_EVENT,
                documentId,
                "MAIL_REPORT",
                manual ? "admin" : "scheduler",
                String.format(
                    "Scheduled mail report export (folderId=%s, accountId=%s, ruleId=%s, days=%d, filename=%s, documentId=%s)",
                    folderId,
                    accountId != null ? accountId : "ALL",
                    ruleId != null ? ruleId : "ALL",
                    Math.max(1, days),
                    filename,
                    documentId
                )
            );
            log.info(
                "Mail report scheduled export complete: folderId={} filename={} documentId={} durationMs={}",
                folderId, filename, documentId, java.time.Duration.between(startedAt, LocalDateTime.now()).toMillis()
            );
            return result;
        } catch (Exception ex) {
            ScheduledExportResult result = ScheduledExportResult.failed(ex.getMessage(), manual, filename, folderId, startedAt, days);
            lastExport.set(result);
            log.warn("Mail report scheduled export errored", ex);
            return result;
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    public record MailReportScheduleStatus(
        boolean enabled,
        String cron,
        UUID folderId,
        int days,
        UUID accountId,
        UUID ruleId,
        ScheduledExportResult lastExport
    ) {}

    public record ScheduledExportResult(
        boolean attempted,
        boolean success,
        String status,
        String message,
        boolean manual,
        String filename,
        UUID folderId,
        UUID documentId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs,
        int days
    ) {
        static ScheduledExportResult skipped(String message, boolean manual) {
            LocalDateTime now = LocalDateTime.now();
            return new ScheduledExportResult(
                false,
                true,
                "SKIPPED",
                message,
                manual,
                null,
                null,
                null,
                now,
                now,
                0L,
                0
            );
        }

        static ScheduledExportResult success(
            boolean manual,
            String filename,
            UUID folderId,
            UUID documentId,
            LocalDateTime startedAt,
            int days
        ) {
            LocalDateTime finishedAt = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
            return new ScheduledExportResult(
                true,
                true,
                "SUCCESS",
                "Exported successfully",
                manual,
                filename,
                folderId,
                documentId,
                startedAt,
                finishedAt,
                durationMs,
                Math.max(1, days)
            );
        }

        static ScheduledExportResult failed(
            String message,
            boolean manual,
            String filename,
            UUID folderId,
            LocalDateTime startedAt,
            int days
        ) {
            LocalDateTime finishedAt = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
            String safeMessage = message != null && !message.isBlank() ? message : "Unknown error";
            return new ScheduledExportResult(
                true,
                false,
                "FAILED",
                safeMessage,
                manual,
                filename,
                folderId,
                null,
                startedAt,
                finishedAt,
                durationMs,
                Math.max(1, days)
            );
        }
    }
}

