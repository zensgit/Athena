package com.ecm.core.asynctask;

import com.ecm.core.entity.AsyncTaskAcknowledgement;
import com.ecm.core.repository.AsyncTaskAcknowledgementRepository;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncTaskAcknowledgementServiceTest {

    private TimeZone originalTimeZone;

    @Mock
    private AsyncTaskAcknowledgementRepository asyncTaskAcknowledgementRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private AsyncTaskAcknowledgementService asyncTaskAcknowledgementService;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    @DisplayName("Fingerprint is stable and acknowledgement metadata is applied to lifecycle tasks")
    void applyAcknowledgementAddsFingerprintAndTimestamp() {
        AsyncTaskStatusSnapshot task = new AsyncTaskStatusSnapshot(
            "preview",
            "Preview",
            "task-1",
            "FAILED",
            "boom",
            Instant.parse("2026-03-21T12:00:00Z"),
            null,
            Instant.parse("2026-03-21T12:05:00Z"),
            null,
            null,
            Instant.parse("2026-03-21T12:05:00Z"),
            "preview.csv",
            "admin",
            "admin",
            null
        );

        AsyncTaskAcknowledgement acknowledgement = AsyncTaskAcknowledgement.builder()
            .userId("admin")
            .domainKey("preview")
            .taskId("task-1")
            .taskStatus("FAILED")
            .taskFingerprint("preview|task-1|FAILED|2026-03-21T12:05:00Z")
            .acknowledgedAt(LocalDateTime.of(2026, 3, 21, 12, 6))
            .build();

        AsyncTaskStatusSnapshot acknowledgedTask = asyncTaskAcknowledgementService.applyAcknowledgement(task, acknowledgement);

        assertEquals("preview|task-1|FAILED|2026-03-21T12:05:00Z", acknowledgedTask.fingerprint());
        assertTrue(acknowledgedTask.acknowledged());
        assertEquals(
            LocalDateTime.of(2026, 3, 21, 12, 6).atZone(originalTimeZone.toZoneId()).toInstant(),
            acknowledgedTask.acknowledgedAt()
        );
    }

    @Test
    @DisplayName("Acknowledged tasks can be filtered out of the recent operator feed")
    void applyAcknowledgementsFiltersAcknowledgedTasks() {
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(asyncTaskAcknowledgementRepository.findByUserIdAndTaskFingerprintIn(any(), any()))
            .thenReturn(List.of(AsyncTaskAcknowledgement.builder()
                .userId("admin")
                .domainKey("search")
                .taskId("task-1")
                .taskStatus("COMPLETED")
                .taskFingerprint("search|task-1|COMPLETED|2026-03-21T11:05:00Z")
                .acknowledgedAt(LocalDateTime.of(2026, 3, 21, 11, 6))
                .build()));

        AsyncTaskStatusSnapshot task = new AsyncTaskStatusSnapshot(
            "search",
            "Search",
            "task-1",
            "COMPLETED",
            null,
            Instant.parse("2026-03-21T11:00:00Z"),
            null,
            Instant.parse("2026-03-21T11:05:00Z"),
            null,
            null,
            Instant.parse("2026-03-21T11:05:00Z"),
            "search.csv",
            null,
            null,
            null
        );

        List<AsyncTaskStatusSnapshot> hidden = asyncTaskAcknowledgementService.applyAcknowledgements(List.of(task), false);
        List<AsyncTaskStatusSnapshot> visible = asyncTaskAcknowledgementService.applyAcknowledgements(List.of(task), true);

        assertTrue(hidden.isEmpty());
        assertEquals(1, visible.size());
        assertTrue(visible.get(0).acknowledged());
    }

    @Test
    @DisplayName("Acknowledging and restoring tasks persists operator ledger entries")
    void acknowledgeAndUnacknowledgePersistsLedgerEntries() {
        when(securityService.getCurrentUser()).thenReturn("admin");
        AsyncTaskStatusSnapshot task = new AsyncTaskStatusSnapshot(
            "preview",
            "Preview",
            "task-2",
            "FAILED",
            "boom",
            Instant.parse("2026-03-21T12:00:00Z"),
            null,
            Instant.parse("2026-03-21T12:05:00Z"),
            null,
            null,
            Instant.parse("2026-03-21T12:05:00Z"),
            "preview.csv",
            "admin",
            "admin",
            null
        );

        AsyncTaskAcknowledgement persistedAcknowledgement = AsyncTaskAcknowledgement.builder()
            .userId("admin")
            .domainKey("preview")
            .taskId("task-2")
            .taskStatus("FAILED")
            .taskFingerprint("preview|task-2|FAILED|2026-03-21T12:05:00Z")
            .taskTimestamp(LocalDateTime.of(2026, 3, 21, 12, 5))
            .acknowledgedAt(LocalDateTime.of(2026, 3, 21, 12, 6))
            .build();

        when(asyncTaskAcknowledgementRepository.findByUserIdAndTaskFingerprint("admin", "preview|task-2|FAILED|2026-03-21T12:05:00Z"))
            .thenReturn(Optional.empty(), Optional.of(persistedAcknowledgement));
        when(asyncTaskAcknowledgementRepository.save(any(AsyncTaskAcknowledgement.class)))
            .thenReturn(persistedAcknowledgement);

        AsyncTaskAcknowledgement acknowledgement = asyncTaskAcknowledgementService.acknowledge(task);
        boolean removed = asyncTaskAcknowledgementService.unacknowledge("preview|task-2|FAILED|2026-03-21T12:05:00Z");

        assertNotNull(acknowledgement);
        assertEquals("preview|task-2|FAILED|2026-03-21T12:05:00Z", acknowledgement.getTaskFingerprint());
        assertTrue(removed);
        verify(asyncTaskAcknowledgementRepository).deleteByUserIdAndTaskFingerprint("admin", "preview|task-2|FAILED|2026-03-21T12:05:00Z");
    }
}
