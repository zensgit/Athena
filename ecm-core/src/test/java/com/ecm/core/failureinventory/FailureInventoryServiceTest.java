package com.ecm.core.failureinventory;

import com.ecm.core.preview.PreviewDeadLetterRegistry;
import com.ecm.core.preview.PreviewDeadLetterRegistry.DeadLetterEntry;
import com.ecm.core.queuebacklog.QueueBacklogObservabilityService;
import com.ecm.core.queuebacklog.QueueBacklogSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FailureInventoryServiceTest {

    private final QueueBacklogObservabilityService queueBacklog = mock(QueueBacklogObservabilityService.class);
    private final PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);

    private final FailureInventoryService service =
        new FailureInventoryService(queueBacklog, previewDeadLetterRegistry);

    private static DeadLetterEntry entry(String category, Instant failedAt) {
        // The entry CARRIES raw `reason` text — the service must never let it reach the DTO.
        return new DeadLetterEntry(
            "doc|thumb", UUID.randomUUID(), "thumb",
            "raw failure reason that must not leak", category, "policy", "RENDER",
            failedAt, 1, 1L, null, 0L);
    }

    private static QueueBacklogSummaryDto backlog(boolean available, long transferFailed, long mailErrors) {
        return new QueueBacklogSummaryDto(
            new QueueBacklogSummaryDto.OcrBacklog(available, 0L, null),
            new QueueBacklogSummaryDto.MailBacklog(available, null, 0.0d, mailErrors, "OK"),
            new QueueBacklogSummaryDto.TransferBacklog(available, 0L, 0L, transferFailed, null, 0L, 60L));
    }

    @Test
    @DisplayName("aggregates the NEW preview dead-letter count (+ category tally + latest) and REUSES transfer/mail counts")
    void aggregatesAllThree() {
        when(queueBacklog.getSummary()).thenReturn(backlog(true, 4L, 2L));
        when(previewDeadLetterRegistry.getItemCount()).thenReturn(3);
        Instant t1 = Instant.parse("2026-06-29T10:00:00Z");
        Instant t2 = Instant.parse("2026-06-29T11:00:00Z");
        Instant t3 = Instant.parse("2026-06-29T12:00:00Z");
        when(previewDeadLetterRegistry.list(anyInt())).thenReturn(List.of(
            entry("TIMEOUT", t1), entry("TIMEOUT", t2), entry("UNKNOWN", t3)));

        FailureInventorySummaryDto dto = service.getSummary();

        // NEW signal
        assertThat(dto.preview().available()).isTrue();
        assertThat(dto.preview().deadLetterCount()).isEqualTo(3L);
        assertThat(dto.preview().categoryTally()).containsEntry("TIMEOUT", 2L).containsEntry("UNKNOWN", 1L);
        assertThat(dto.preview().latestFailedAt()).isEqualTo(t3);
        // REUSED counts
        assertThat(dto.transfer().available()).isTrue();
        assertThat(dto.transfer().failedCount()).isEqualTo(4L);
        assertThat(dto.mail().available()).isTrue();
        assertThat(dto.mail().errorAccountCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("a failing source reports available=false and the service never throws")
    void failingSourceIsIsolated() {
        when(previewDeadLetterRegistry.getItemCount()).thenThrow(new RuntimeException("redis down"));
        when(queueBacklog.getSummary()).thenThrow(new RuntimeException("backlog down"));

        FailureInventorySummaryDto dto = service.getSummary();

        assertThat(dto.preview().available()).isFalse();
        assertThat(dto.preview().deadLetterCount()).isZero();
        assertThat(dto.preview().categoryTally()).isEmpty();
        assertThat(dto.preview().latestFailedAt()).isNull();
        assertThat(dto.transfer().available()).isFalse();
        assertThat(dto.transfer().failedCount()).isZero();
        assertThat(dto.mail().available()).isFalse();
        assertThat(dto.mail().errorAccountCount()).isZero();
    }

    @Test
    @DisplayName("an upstream source marked available=false is propagated, not silently shown as 0/available")
    void propagatesUpstreamUnavailable() {
        when(queueBacklog.getSummary()).thenReturn(backlog(false, 0L, 0L));
        when(previewDeadLetterRegistry.getItemCount()).thenReturn(0);
        when(previewDeadLetterRegistry.list(anyInt())).thenReturn(List.of());

        FailureInventorySummaryDto dto = service.getSummary();

        assertThat(dto.transfer().available()).isFalse();
        assertThat(dto.mail().available()).isFalse();
        // preview source itself was fine -> available=true with an empty/zero result
        assertThat(dto.preview().available()).isTrue();
        assertThat(dto.preview().deadLetterCount()).isZero();
    }

    @Test
    @DisplayName("§5A PII guard: the DTO exposes ONLY count/timestamp/type/category — no raw failure text fields")
    void dtoExposesNoRawFailureText() {
        Set<String> forbidden = Set.of(
            "reason", "subject", "errorMessage", "transportMessage", "errorLog", "lastMessage", "entryReport");
        Set<String> allowed = Set.of(
            // top-level
            "preview", "transfer", "mail",
            // sub-records
            "available", "deadLetterCount", "categoryTally", "latestFailedAt", "failedCount", "errorAccountCount");

        List<Class<?>> records = List.of(
            FailureInventorySummaryDto.class,
            FailureInventorySummaryDto.PreviewDeadLetter.class,
            FailureInventorySummaryDto.TransferFailures.class,
            FailureInventorySummaryDto.MailFetchErrors.class);

        for (Class<?> rec : records) {
            for (RecordComponent c : rec.getRecordComponents()) {
                assertThat(forbidden)
                    .as("%s.%s must not be a raw-failure-text field", rec.getSimpleName(), c.getName())
                    .doesNotContain(c.getName());
                assertThat(allowed)
                    .as("%s.%s must be an explicitly-allowed count/time/type field", rec.getSimpleName(), c.getName())
                    .contains(c.getName());
            }
        }
    }
}
