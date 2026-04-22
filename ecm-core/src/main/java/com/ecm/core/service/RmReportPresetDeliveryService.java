package com.ecm.core.service;

import com.ecm.core.entity.RmReportPreset;
import com.ecm.core.entity.RmReportPresetExecution;
import com.ecm.core.entity.RmReportPresetExecution.ExecutionStatus;
import com.ecm.core.entity.RmReportPresetExecution.TriggerType;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.repository.RmReportPresetExecutionRepository;
import com.ecm.core.repository.RmReportPresetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RmReportPresetDeliveryService {

    private static final String AUDIT_SCHEDULE_UPDATED = "RM_REPORT_PRESET_SCHEDULE_UPDATED";
    private static final String AUDIT_DELIVERED = "RM_REPORT_PRESET_DELIVERED";
    private static final String AUDIT_DELIVERY_FAILED = "RM_REPORT_PRESET_DELIVERY_FAILED";

    private final RmReportPresetService presetService;
    private final RmReportPresetRepository presetRepository;
    private final RmReportPresetExecutionRepository executionRepository;
    private final RecordsManagementService recordsManagementService;
    private final DocumentUploadService uploadService;
    private final AuditService auditService;
    private final SecurityService securityService;

    @Transactional(readOnly = true)
    public ScheduleStatusDto getSchedule(UUID presetId) {
        RmReportPreset preset = presetService.getOwned(presetId);
        return toScheduleStatus(preset, latestExecution(preset.getId()));
    }

    @Transactional(readOnly = true)
    public ScheduledDeliveryTelemetryDto getScheduledDeliveryTelemetry() {
        String owner = securityService.getCurrentUser();
        if (owner == null || owner.isBlank()) {
            throw new IllegalStateException("Current user is required for scheduled delivery telemetry");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(24);

        long scheduleEnabledCount =
            presetRepository.countByOwnerAndScheduleEnabledTrueAndDeletedFalse(owner);
        long duePresetCount =
            presetRepository.countByOwnerAndScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqual(owner, now);
        long last24hSuccessCount = executionRepository
            .countByOwnerAndStatusAndStartedAtGreaterThanEqual(owner, ExecutionStatus.SUCCESS, since);
        long last24hFailedCount = executionRepository
            .countByOwnerAndStatusAndStartedAtGreaterThanEqual(owner, ExecutionStatus.FAILED, since);

        LocalDateTime lastExecutionAt = executionRepository
            .findFirstByOwnerOrderByStartedAtDesc(owner)
            .map(RmReportPresetExecution::getStartedAt)
            .orElse(null);

        return new ScheduledDeliveryTelemetryDto(
            scheduleEnabledCount,
            duePresetCount,
            last24hSuccessCount,
            last24hFailedCount,
            lastExecutionAt,
            now
        );
    }

    public ScheduleStatusDto updateSchedule(UUID presetId, UpdateScheduleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Schedule request is required");
        }
        RmReportPreset preset = presetService.getOwned(presetId);
        if (Boolean.TRUE.equals(request.enabled())) {
            assertSchedulableKind(preset);
            String cronExpression = ScheduledRuleValidation.normalizeCronExpression(request.cronExpression());
            if (cronExpression == null) {
                throw new IllegalArgumentException("Cron expression is required when schedule is enabled");
            }
            if (request.deliveryFolderId() == null) {
                throw new IllegalArgumentException("Delivery folder id is required when schedule is enabled");
            }
            String timezone = ScheduledRuleValidation.normalizeTimezone(request.timezone());
            ScheduledRuleValidation.ValidatedSchedule schedule =
                ScheduledRuleValidation.validateAndBuild(cronExpression, timezone, 1);
            preset.setScheduleEnabled(true);
            preset.setCronExpression(schedule.cronExpression());
            preset.setScheduleTimezone(schedule.timezone());
            preset.setDeliveryFolderId(request.deliveryFolderId());
            preset.setNextRunAt(schedule.nextRunAt());
        } else {
            preset.setScheduleEnabled(false);
            preset.setCronExpression(null);
            preset.setNextRunAt(null);
            if (request.timezone() != null) {
                preset.setScheduleTimezone(ScheduledRuleValidation.normalizeTimezone(request.timezone()));
            }
            if (request.deliveryFolderId() != null) {
                preset.setDeliveryFolderId(request.deliveryFolderId());
            }
        }
        RmReportPreset saved = presetRepository.save(preset);
        auditService.logEvent(
            AUDIT_SCHEDULE_UPDATED,
            saved.getId(),
            saved.getName(),
            saved.getOwner(),
            String.format(
                "RM report preset schedule updated (enabled=%s, cron=%s, timezone=%s, folderId=%s, nextRunAt=%s)",
                saved.isScheduleEnabled(),
                saved.getCronExpression(),
                saved.getScheduleTimezone(),
                saved.getDeliveryFolderId(),
                saved.getNextRunAt()
            )
        );
        return toScheduleStatus(saved, latestExecution(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<PresetExecutionDto> listExecutions(UUID presetId, Integer limit) {
        RmReportPreset preset = presetService.getOwned(presetId);
        int effectiveLimit = normalizeExecutionLimit(limit);
        return executionRepository.findByPresetIdOrderByStartedAtDesc(
                preset.getId(),
                PageRequest.of(0, effectiveLimit)
            ).stream()
            .map(this::toExecutionDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<PresetExecutionDto> listExecutionLedger(
        UUID presetId,
        ExecutionStatus status,
        TriggerType triggerType,
        LocalDateTime from,
        LocalDateTime to,
        Integer page,
        Integer size
    ) {
        UUID ownedPresetId = normalizeOwnedPresetId(presetId);
        Pageable pageable = PageRequest.of(
            normalizeLedgerPage(page),
            normalizeLedgerPageSize(size),
            Sort.by(Sort.Direction.DESC, "startedAt")
        );
        String owner = requireCurrentOwner();
        Page<RmReportPresetExecution> result = executionRepository.findAll(
            executionLedgerSpec(owner, ownedPresetId, status, triggerType, from, to),
            pageable
        );
        return result.map(this::toExecutionDto);
    }

    @Transactional(readOnly = true)
    public String exportExecutionLedgerCsv(
        UUID presetId,
        ExecutionStatus status,
        TriggerType triggerType,
        LocalDateTime from,
        LocalDateTime to,
        Integer limit
    ) {
        UUID ownedPresetId = normalizeOwnedPresetId(presetId);
        String owner = requireCurrentOwner();
        List<RmReportPresetExecution> executions = executionRepository.findAll(
            executionLedgerSpec(owner, ownedPresetId, status, triggerType, from, to),
            PageRequest.of(0, normalizeLedgerExportLimit(limit), Sort.by(Sort.Direction.DESC, "startedAt"))
        ).getContent();

        auditService.logEvent(
            "RM_REPORT_PRESET_EXECUTION_LEDGER_EXPORTED",
            ownedPresetId,
            "rm-report-preset-execution-ledger",
            owner,
            String.format(
                "Exported %d preset delivery executions (presetId=%s, status=%s, triggerType=%s, from=%s, to=%s)",
                executions.size(),
                ownedPresetId,
                status,
                triggerType,
                from,
                to
            )
        );

        return buildExecutionLedgerCsv(executions);
    }

    public PresetExecutionDto deliverNow(UUID presetId) {
        RmReportPreset preset = presetService.getOwned(presetId);
        return deliverPreset(preset, TriggerType.MANUAL, false);
    }

    @Scheduled(cron = "${ecm.rm.report-presets.scheduler-cron:0 */5 * * * *}")
    public void runScheduledDeliveries() {
        LocalDateTime now = LocalDateTime.now();
        List<RmReportPreset> duePresets =
            presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(now);
        for (RmReportPreset preset : duePresets) {
            if (!claimScheduledRun(preset)) {
                continue;
            }
            RmReportPreset claimedPreset = presetRepository.findByIdAndDeletedFalse(preset.getId()).orElse(null);
            if (claimedPreset == null) {
                log.warn("RM report preset {} disappeared after claim; skipping scheduled delivery", preset.getId());
                continue;
            }
            SecurityContext previous = pushPresetAuthentication(claimedPreset);
            try {
                deliverPreset(claimedPreset, TriggerType.SCHEDULED, true, true);
            } catch (Exception ex) {
                log.warn("RM report preset scheduled delivery errored for {}: {}", claimedPreset.getId(), ex.getMessage(), ex);
            } finally {
                popAuthentication(previous);
            }
        }
    }

    private boolean claimScheduledRun(RmReportPreset preset) {
        if (!preset.isScheduleEnabled() || preset.getCronExpression() == null || preset.getNextRunAt() == null) {
            return false;
        }
        LocalDateTime claimedNextRunAt = ScheduledRuleValidation.computeNextRunAt(
            preset.getCronExpression(),
            preset.getScheduleTimezone(),
            preset.getNextRunAt()
        );
        if (claimedNextRunAt == null) {
            log.warn("RM report preset {} could not compute next scheduled run; skipping claim", preset.getId());
            return false;
        }
        int updated = presetRepository.claimScheduledRun(
            preset.getId(),
            preset.getNextRunAt(),
            claimedNextRunAt
        );
        if (updated == 0) {
            log.debug("RM report preset {} was already claimed by another runner", preset.getId());
            return false;
        }
        return true;
    }

    private PresetExecutionDto deliverPreset(RmReportPreset preset, TriggerType triggerType, boolean scheduledRun) {
        return deliverPreset(preset, triggerType, scheduledRun, false);
    }

    private PresetExecutionDto deliverPreset(
        RmReportPreset preset,
        TriggerType triggerType,
        boolean scheduledRun,
        boolean nextRunAlreadyClaimed
    ) {
        assertSchedulableKind(preset);
        UUID folderId = preset.getDeliveryFolderId();
        if (folderId == null) {
            throw new IllegalArgumentException("Delivery folder id is required for preset delivery");
        }

        LocalDateTime startedAt = LocalDateTime.now();
        String filename = buildFilename(preset);
        try {
            String csv = renderCsv(preset);
            MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
            );

            PipelineResult uploadResult = uploadService.uploadDocument(file, folderId, null);
            if (!uploadResult.isSuccess()) {
                String message = uploadResult.getErrors() != null ? uploadResult.getErrors().toString() : "Unknown upload error";
                return persistFailedExecution(
                    preset,
                    triggerType,
                    startedAt,
                    filename,
                    folderId,
                    message,
                    scheduledRun,
                    nextRunAlreadyClaimed
                );
            }

            UUID documentId = uploadResult.getDocumentId();
            LocalDateTime finishedAt = LocalDateTime.now();
            long durationMs = Duration.between(startedAt, finishedAt).toMillis();
            preset.setLastRunAt(finishedAt);
            if (scheduledRun && !nextRunAlreadyClaimed && preset.isScheduleEnabled() && preset.getCronExpression() != null) {
                preset.setNextRunAt(ScheduledRuleValidation.computeNextRunAt(
                    preset.getCronExpression(),
                    preset.getScheduleTimezone(),
                    finishedAt
                ));
            }
            presetRepository.save(preset);

            RmReportPresetExecution execution = new RmReportPresetExecution();
            execution.setPreset(preset);
            execution.setOwner(preset.getOwner());
            execution.setTriggerType(triggerType);
            execution.setStatus(ExecutionStatus.SUCCESS);
            execution.setFilename(filename);
            execution.setTargetFolderId(folderId);
            execution.setDocumentId(documentId);
            execution.setMessage("Delivered successfully");
            execution.setStartedAt(startedAt);
            execution.setFinishedAt(finishedAt);
            execution.setDurationMs(durationMs);
            execution = executionRepository.save(execution);

            auditService.logEvent(
                AUDIT_DELIVERED,
                preset.getId(),
                preset.getName(),
                preset.getOwner(),
                String.format(
                    "RM report preset delivered (trigger=%s, folderId=%s, documentId=%s, filename=%s)",
                    triggerType, folderId, documentId, filename
                )
            );
            return toExecutionDto(execution);
        } catch (Exception ex) {
            return persistFailedExecution(
                preset,
                triggerType,
                startedAt,
                filename,
                folderId,
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(),
                scheduledRun,
                nextRunAlreadyClaimed
            );
        }
    }

    private PresetExecutionDto persistFailedExecution(
        RmReportPreset preset,
        TriggerType triggerType,
        LocalDateTime startedAt,
        String filename,
        UUID folderId,
        String message,
        boolean scheduledRun,
        boolean nextRunAlreadyClaimed
    ) {
        LocalDateTime finishedAt = LocalDateTime.now();
        long durationMs = Duration.between(startedAt, finishedAt).toMillis();
        if (scheduledRun && !nextRunAlreadyClaimed && preset.isScheduleEnabled() && preset.getCronExpression() != null) {
            preset.setNextRunAt(ScheduledRuleValidation.computeNextRunAt(
                preset.getCronExpression(),
                preset.getScheduleTimezone(),
                finishedAt
            ));
        }
        presetRepository.save(preset);

        RmReportPresetExecution execution = new RmReportPresetExecution();
        execution.setPreset(preset);
        execution.setOwner(preset.getOwner());
        execution.setTriggerType(triggerType);
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setFilename(filename);
        execution.setTargetFolderId(folderId);
        execution.setMessage(message != null && !message.isBlank() ? message : "Unknown error");
        execution.setStartedAt(startedAt);
        execution.setFinishedAt(finishedAt);
        execution.setDurationMs(durationMs);
        execution = executionRepository.save(execution);

        auditService.logEvent(
            AUDIT_DELIVERY_FAILED,
            preset.getId(),
            preset.getName(),
            preset.getOwner(),
            String.format(
                "RM report preset delivery failed (trigger=%s, folderId=%s, filename=%s, message=%s)",
                triggerType, folderId, filename, execution.getMessage()
            )
        );
        return toExecutionDto(execution);
    }

    private void assertSchedulableKind(RmReportPreset preset) {
        if (!supportsScheduledDelivery(preset.getKind())) {
            throw new IllegalArgumentException("Scheduled delivery is not supported for preset kind " + preset.getKind());
        }
    }

    private boolean supportsScheduledDelivery(RmReportPreset.Kind kind) {
        return kind == RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT
            || kind == RmReportPreset.Kind.ACTIVITY_FAMILY_HIGHLIGHTS
            || kind == RmReportPreset.Kind.ACTIVITY_FAMILY_MIX
            || kind == RmReportPreset.Kind.ACTIVITY_EVENT_TYPE_REPORT
            || kind == RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT
            || kind == RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_FAMILY_REPORT
            || kind == RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT;
    }

    private String renderCsv(RmReportPreset preset) {
        return switch (preset.getKind()) {
            case ACTIVITY_FAMILY_REPORT -> buildActivityFamilyReportCsv(
                recordsManagementService.getActivityFamilyReport(
                    requirePresetDateTimeParam(preset, "from"),
                    requirePresetDateTimeParam(preset, "to"),
                    optionalPresetIntegerParam(preset, "eventTypeLimit"),
                    optionalPresetIntegerParam(preset, "contributorLimit")
                )
            );
            case ACTIVITY_EVENT_TYPE_REPORT -> buildActivityEventTypeReportCsv(
                recordsManagementService.getActivityEventTypeReport(
                    requirePresetDateTimeParam(preset, "from"),
                    requirePresetDateTimeParam(preset, "to"),
                    optionalPresetIntegerParam(preset, "limit")
                )
            );
            case ACTIVITY_CONTRIBUTOR_REPORT -> buildActivityContributorReportCsv(
                recordsManagementService.getActivityContributorReport(
                    requirePresetDateTimeParam(preset, "from"),
                    requirePresetDateTimeParam(preset, "to"),
                    optionalPresetIntegerParam(preset, "limit"),
                    optionalPresetIntegerParam(preset, "eventTypeLimit")
                )
            );
            case ACTIVITY_CONTRIBUTOR_FAMILY_REPORT -> buildActivityContributorFamilyReportCsv(
                recordsManagementService.getActivityContributorFamilyReport(
                    requirePresetDateTimeParam(preset, "from"),
                    requirePresetDateTimeParam(preset, "to"),
                    optionalPresetIntegerParam(preset, "limit")
                )
            );
            case ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT -> buildActivityContributorEventTypeReportCsv(
                recordsManagementService.getActivityContributorEventTypeReport(
                    requirePresetDateTimeParam(preset, "from"),
                    requirePresetDateTimeParam(preset, "to"),
                    optionalPresetIntegerParam(preset, "limit"),
                    optionalPresetIntegerParam(preset, "eventTypeLimit")
                )
            );
            case ACTIVITY_FAMILY_HIGHLIGHTS -> {
                ResolvedPresetDateTimeRange range = requirePresetRollingDateTimeRange(preset, "windowDays");
                yield buildActivityFamilyReportCsv(
                    recordsManagementService.getActivityFamilyReport(range.from(), range.to(), null, null)
                );
            }
            case ACTIVITY_FAMILY_MIX -> {
                ResolvedPresetDateTimeRange range = requirePresetRollingDateTimeRange(preset, "days");
                yield buildActivityFamilyReportCsv(
                    recordsManagementService.getActivityFamilyReport(range.from(), range.to(), null, null)
                );
            }
            default -> throw new IllegalArgumentException("Scheduled delivery is not supported for preset kind " + preset.getKind());
        };
    }

    private String buildFilename(RmReportPreset preset) {
        String base = preset.getName() == null || preset.getName().isBlank()
            ? "rm-report-preset"
            : preset.getName().trim().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        return base + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
    }

    private SecurityContext pushPresetAuthentication(RmReportPreset preset) {
        String actor = preset.getOwner() != null && !preset.getOwner().isBlank() ? preset.getOwner() : "system";
        SecurityContext previous = SecurityContextHolder.getContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            actor,
            "scheduled-rm-report-preset",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        return previous;
    }

    private void popAuthentication(SecurityContext previous) {
        if (previous != null) {
            SecurityContextHolder.setContext(previous);
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    private ScheduleStatusDto toScheduleStatus(RmReportPreset preset, RmReportPresetExecution latestExecution) {
        return new ScheduleStatusDto(
            preset.getId(),
            preset.isScheduleEnabled(),
            preset.getCronExpression(),
            preset.getScheduleTimezone(),
            preset.getDeliveryFolderId(),
            preset.getNextRunAt(),
            preset.getLastRunAt(),
            latestExecution != null ? toExecutionDto(latestExecution) : null
        );
    }

    private RmReportPresetExecution latestExecution(UUID presetId) {
        return executionRepository.findFirstByPresetIdOrderByStartedAtDesc(presetId).orElse(null);
    }

    private PresetExecutionDto toExecutionDto(RmReportPresetExecution execution) {
        return new PresetExecutionDto(
            execution.getId(),
            execution.getPreset().getId(),
            execution.getPreset().getName(),
            execution.getPreset().getKind(),
            execution.getTriggerType(),
            execution.getStatus(),
            execution.getFilename(),
            execution.getTargetFolderId(),
            execution.getDocumentId(),
            execution.getMessage(),
            execution.getStartedAt(),
            execution.getFinishedAt(),
            execution.getDurationMs()
        );
    }

    private int normalizeExecutionLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        return Math.max(1, Math.min(limit, 100));
    }

    private int normalizeLedgerPage(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    private int normalizeLedgerPageSize(Integer size) {
        if (size == null) {
            return 20;
        }
        return Math.max(1, Math.min(size, 100));
    }

    private int normalizeLedgerExportLimit(Integer limit) {
        if (limit == null) {
            return 200;
        }
        return Math.max(1, Math.min(limit, 1000));
    }

    private UUID normalizeOwnedPresetId(UUID presetId) {
        if (presetId == null) {
            return null;
        }
        return presetService.getOwned(presetId).getId();
    }

    private String requireCurrentOwner() {
        String owner = securityService.getCurrentUser();
        if (owner == null || owner.isBlank()) {
            throw new SecurityException("No authenticated user for preset execution ledger operation");
        }
        return owner;
    }

    private Specification<RmReportPresetExecution> executionLedgerSpec(
        String owner,
        UUID presetId,
        ExecutionStatus status,
        TriggerType triggerType,
        LocalDateTime from,
        LocalDateTime to
    ) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("owner"), owner));
            if (presetId != null) {
                predicates.add(cb.equal(root.get("preset").get("id"), presetId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (triggerType != null) {
                predicates.add(cb.equal(root.get("triggerType"), triggerType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), to));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private LocalDateTime requirePresetDateTimeParam(RmReportPreset preset, String key) {
        String value = requirePresetStringParam(preset, key);
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                "Preset param \"" + key + "\" must be an ISO-8601 datetime for " + preset.getKind()
            );
        }
    }

    private String requirePresetStringParam(RmReportPreset preset, String key) {
        Object value = presetParam(preset, key);
        if (value == null) {
            throw new IllegalArgumentException(
                "Preset param \"" + key + "\" is required for " + preset.getKind()
            );
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim();
        }
        throw new IllegalArgumentException(
            "Preset param \"" + key + "\" must be a non-blank string for " + preset.getKind()
        );
    }

    private Integer optionalPresetIntegerParam(RmReportPreset preset, String key) {
        Object value = presetParam(preset, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            double doubleValue = numberValue.doubleValue();
            if (Double.isFinite(doubleValue) && Math.rint(doubleValue) == doubleValue) {
                return numberValue.intValue();
            }
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.valueOf(stringValue.trim());
            } catch (NumberFormatException ignored) {
                // Fall through.
            }
        }
        throw new IllegalArgumentException(
            "Preset param \"" + key + "\" must be an integer for " + preset.getKind()
        );
    }

    private LocalDateTime optionalPresetDateTimeParam(RmReportPreset preset, String key) {
        Object value = presetParam(preset, key);
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return LocalDateTime.parse(stringValue.trim());
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(
                    "Preset param \"" + key + "\" must be an ISO-8601 datetime for " + preset.getKind()
                );
            }
        }
        throw new IllegalArgumentException(
            "Preset param \"" + key + "\" must be an ISO-8601 datetime for " + preset.getKind()
        );
    }

    private Integer resolvePresetRollingDays(RmReportPreset preset, String key) {
        Integer explicitDays = optionalPresetIntegerParam(preset, key);
        if (explicitDays != null) {
            return explicitDays;
        }
        LocalDateTime from = optionalPresetDateTimeParam(preset, "from");
        LocalDateTime to = optionalPresetDateTimeParam(preset, "to");
        if (from != null && to != null) {
            long dayCount = java.time.temporal.ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1L;
            return (int) Math.max(dayCount, 1L);
        }
        return null;
    }

    private ResolvedPresetDateTimeRange requirePresetRollingDateTimeRange(RmReportPreset preset, String dayKey) {
        LocalDateTime from = optionalPresetDateTimeParam(preset, "from");
        LocalDateTime to = optionalPresetDateTimeParam(preset, "to");
        if (from != null && to != null) {
            return new ResolvedPresetDateTimeRange(from, to);
        }
        Integer days = resolvePresetRollingDays(preset, dayKey);
        if (days == null || days < 1) {
            throw new IllegalArgumentException(
                "Preset param \"" + dayKey + "\" is required for scheduled delivery on " + preset.getKind()
            );
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        return new ResolvedPresetDateTimeRange(startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
    }

    private Object presetParam(RmReportPreset preset, String key) {
        Map<String, Object> params = preset.getParams();
        return params == null ? null : params.get(key);
    }

    private String buildActivityContributorFamilyReportCsv(RecordsManagementService.ActivityContributorFamilyReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("username,label,family,currentCount,previousCount,delta,lastEventTime,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityContributorFamilyReportEntryDto contributor : report.contributors()) {
            for (RecordsManagementService.ActivityContributorFamilyReportFamilyDto family : contributor.families()) {
                sb.append(csvEscape(contributor.username())).append(',')
                    .append(csvEscape(contributor.label())).append(',')
                    .append(csvEscape(family.family())).append(',')
                    .append(family.currentCount()).append(',')
                    .append(family.previousCount()).append(',')
                    .append(family.delta()).append(',')
                    .append(csvEscape(family.lastEventTime())).append(',')
                    .append(csvEscape(report.currentWindow().from())).append(',')
                    .append(csvEscape(report.currentWindow().to())).append(',')
                    .append(csvEscape(report.previousWindow().from())).append(',')
                    .append(csvEscape(report.previousWindow().to()))
                    .append('\n');
            }
        }
        return sb.toString();
    }

    private String buildActivityContributorReportCsv(RecordsManagementService.ActivityContributorReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("username,label,currentCount,previousCount,delta,lastEventTime,currentTopEventTypes,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityContributorReportEntryDto entry : report.contributors()) {
            sb.append(csvEscape(entry.username())).append(',')
                .append(csvEscape(entry.label())).append(',')
                .append(entry.currentCount()).append(',')
                .append(entry.previousCount()).append(',')
                .append(entry.delta()).append(',')
                .append(csvEscape(entry.lastEventTime())).append(',')
                .append(csvEscape(joinContributorReportEventTypes(entry.currentTopEventTypes()))).append(',')
                .append(csvEscape(report.currentWindow().from())).append(',')
                .append(csvEscape(report.currentWindow().to())).append(',')
                .append(csvEscape(report.previousWindow().from())).append(',')
                .append(csvEscape(report.previousWindow().to()))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildActivityContributorEventTypeReportCsv(RecordsManagementService.ActivityContributorEventTypeReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("username,label,eventType,family,currentCount,previousCount,delta,lastEventTime,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityContributorEventTypeReportEntryDto contributor : report.contributors()) {
            for (RecordsManagementService.ActivityContributorEventTypeReportEventTypeDto eventType : contributor.eventTypes()) {
                sb.append(csvEscape(contributor.username())).append(',')
                    .append(csvEscape(contributor.label())).append(',')
                    .append(csvEscape(eventType.eventType())).append(',')
                    .append(csvEscape(eventType.family())).append(',')
                    .append(eventType.currentCount()).append(',')
                    .append(eventType.previousCount()).append(',')
                    .append(eventType.delta()).append(',')
                    .append(csvEscape(eventType.lastEventTime())).append(',')
                    .append(csvEscape(report.currentWindow().from())).append(',')
                    .append(csvEscape(report.currentWindow().to())).append(',')
                    .append(csvEscape(report.previousWindow().from())).append(',')
                    .append(csvEscape(report.previousWindow().to()))
                    .append('\n');
            }
        }
        return sb.toString();
    }

    private String buildActivityEventTypeReportCsv(RecordsManagementService.ActivityEventTypeReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("eventType,family,currentCount,previousCount,delta,lastEventTime,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityEventTypeReportEntryDto entry : report.eventTypes()) {
            sb.append(csvEscape(entry.eventType())).append(',')
                .append(csvEscape(entry.family())).append(',')
                .append(entry.currentCount()).append(',')
                .append(entry.previousCount()).append(',')
                .append(entry.delta()).append(',')
                .append(csvEscape(entry.lastEventTime())).append(',')
                .append(csvEscape(report.currentWindow().from())).append(',')
                .append(csvEscape(report.currentWindow().to())).append(',')
                .append(csvEscape(report.previousWindow().from())).append(',')
                .append(csvEscape(report.previousWindow().to()))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildActivityFamilyReportCsv(RecordsManagementService.ActivityFamilyReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("family,currentCount,previousCount,delta,lastEventTime,topEventTypes,topContributors,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityFamilyReportEntryDto entry : report.families()) {
            sb.append(csvEscape(entry.family())).append(',')
                .append(entry.currentCount()).append(',')
                .append(entry.previousCount()).append(',')
                .append(entry.delta()).append(',')
                .append(csvEscape(entry.lastEventTime())).append(',')
                .append(csvEscape(joinReportEventTypes(entry.topEventTypes()))).append(',')
                .append(csvEscape(joinReportContributors(entry.topContributors()))).append(',')
                .append(csvEscape(report.currentWindow().from())).append(',')
                .append(csvEscape(report.currentWindow().to())).append(',')
                .append(csvEscape(report.previousWindow().from())).append(',')
                .append(csvEscape(report.previousWindow().to()))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildExecutionLedgerCsv(List<RmReportPresetExecution> executions) {
        StringBuilder sb = new StringBuilder();
        sb.append("executionId,presetId,presetName,presetKind,triggerType,status,filename,targetFolderId,documentId,message,startedAt,finishedAt,durationMs\n");
        for (RmReportPresetExecution execution : executions) {
            sb.append(csvEscape(execution.getId())).append(',')
                .append(csvEscape(execution.getPreset().getId())).append(',')
                .append(csvEscape(execution.getPreset().getName())).append(',')
                .append(csvEscape(execution.getPreset().getKind())).append(',')
                .append(csvEscape(execution.getTriggerType())).append(',')
                .append(csvEscape(execution.getStatus())).append(',')
                .append(csvEscape(execution.getFilename())).append(',')
                .append(csvEscape(execution.getTargetFolderId())).append(',')
                .append(csvEscape(execution.getDocumentId())).append(',')
                .append(csvEscape(execution.getMessage())).append(',')
                .append(csvEscape(execution.getStartedAt())).append(',')
                .append(csvEscape(execution.getFinishedAt())).append(',')
                .append(csvEscape(execution.getDurationMs()))
                .append('\n');
        }
        return sb.toString();
    }

    private String joinReportEventTypes(List<RecordsManagementService.ActivityFamilyReportEventTypeDto> eventTypes) {
        return eventTypes.stream()
            .map(item -> item.eventType() + " (" + item.count() + ")")
            .reduce((left, right) -> left + "; " + right)
            .orElse("");
    }

    private String joinReportContributors(List<RecordsManagementService.ActivityFamilyReportContributorDto> contributors) {
        return contributors.stream()
            .map(item -> item.label() + " (" + item.count() + ")")
            .reduce((left, right) -> left + "; " + right)
            .orElse("");
    }

    private String joinContributorReportEventTypes(List<RecordsManagementService.ActivityContributorReportEventTypeDto> eventTypes) {
        return eventTypes.stream()
            .map(item -> item.eventType() + " [" + item.family() + "] (" + item.count() + ")")
            .reduce((left, right) -> left + "; " + right)
            .orElse("");
    }

    private String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String stringValue = String.valueOf(value);
        if (stringValue.contains(",") || stringValue.contains("\"") || stringValue.contains("\n")) {
            return "\"" + stringValue.replace("\"", "\"\"") + "\"";
        }
        return stringValue;
    }

    public record UpdateScheduleRequest(
        Boolean enabled,
        String cronExpression,
        String timezone,
        UUID deliveryFolderId
    ) {
    }

    private record ResolvedPresetDateTimeRange(
        LocalDateTime from,
        LocalDateTime to
    ) {
    }

    public record ScheduledDeliveryTelemetryDto(
        long scheduleEnabledCount,
        long duePresetCount,
        long last24hSuccessCount,
        long last24hFailedCount,
        LocalDateTime lastExecutionAt,
        LocalDateTime generatedAt
    ) {
    }

    public record ScheduleStatusDto(
        UUID presetId,
        boolean enabled,
        String cronExpression,
        String timezone,
        UUID deliveryFolderId,
        LocalDateTime nextRunAt,
        LocalDateTime lastRunAt,
        PresetExecutionDto lastExecution
    ) {
    }

    public record PresetExecutionDto(
        UUID id,
        UUID presetId,
        String presetName,
        RmReportPreset.Kind presetKind,
        TriggerType triggerType,
        ExecutionStatus status,
        String filename,
        UUID targetFolderId,
        UUID documentId,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs
    ) {
    }
}
