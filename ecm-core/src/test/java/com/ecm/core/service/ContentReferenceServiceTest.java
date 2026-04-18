package com.ecm.core.service;

import com.ecm.core.entity.ContentReference;
import com.ecm.core.entity.ContentReference.OwnerType;
import com.ecm.core.repository.ContentReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentReferenceService")
class ContentReferenceServiceTest {

    @Mock
    private ContentReferenceRepository contentReferenceRepository;

    @Mock
    private ContentService contentService;

    private ContentReferenceService service;

    @BeforeEach
    void setUp() {
        service = new ContentReferenceService(contentReferenceRepository, contentService);
        ReflectionTestUtils.setField(service, "ledgerEnabled", true);
        ReflectionTestUtils.setField(service, "orphanCleanupEnabled", false);
        ReflectionTestUtils.setField(service, "orphanCleanupGraceHours", 24);
    }

    @Nested
    @DisplayName("attach")
    class Attach {

        @Test
        @DisplayName("creates new reference for document owner")
        void createsNewReferenceForDocumentOwner() {
            String contentId = "20260413_abc123";
            UUID ownerId = UUID.randomUUID();

            when(contentReferenceRepository.findByContentIdAndOwnerTypeAndOwnerId(
                    contentId, OwnerType.DOCUMENT, ownerId))
                    .thenReturn(Optional.empty());
            when(contentReferenceRepository.save(any(ContentReference.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ContentReference result = service.attach(contentId, OwnerType.DOCUMENT, ownerId);

            assertNotNull(result);
            assertEquals(contentId, result.getContentId());
            assertEquals(OwnerType.DOCUMENT, result.getOwnerType());
            assertEquals(ownerId, result.getOwnerId());
            assertTrue(result.isActive());

            verify(contentReferenceRepository).save(any(ContentReference.class));
        }

        @Test
        @DisplayName("creates new reference for version owner")
        void createsNewReferenceForVersionOwner() {
            String contentId = "20260413_def456";
            UUID ownerId = UUID.randomUUID();

            when(contentReferenceRepository.findByContentIdAndOwnerTypeAndOwnerId(
                    contentId, OwnerType.VERSION, ownerId))
                    .thenReturn(Optional.empty());
            when(contentReferenceRepository.save(any(ContentReference.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ContentReference result = service.attach(contentId, OwnerType.VERSION, ownerId);

            assertNotNull(result);
            assertEquals(OwnerType.VERSION, result.getOwnerType());
        }

        @Test
        @DisplayName("is idempotent when reference already active")
        void idempotentWhenAlreadyActive() {
            String contentId = "20260413_abc123";
            UUID ownerId = UUID.randomUUID();
            ContentReference existing = ContentReference.builder()
                    .contentId(contentId)
                    .ownerType(OwnerType.DOCUMENT)
                    .ownerId(ownerId)
                    .active(true)
                    .build();

            when(contentReferenceRepository.findByContentIdAndOwnerTypeAndOwnerId(
                    contentId, OwnerType.DOCUMENT, ownerId))
                    .thenReturn(Optional.of(existing));

            ContentReference result = service.attach(contentId, OwnerType.DOCUMENT, ownerId);

            assertSame(existing, result);
            verify(contentReferenceRepository, never()).save(any());
        }

        @Test
        @DisplayName("reactivates previously deactivated reference")
        void reactivatesPreviouslyDeactivated() {
            String contentId = "20260413_abc123";
            UUID ownerId = UUID.randomUUID();
            ContentReference existing = ContentReference.builder()
                    .contentId(contentId)
                    .ownerType(OwnerType.DOCUMENT)
                    .ownerId(ownerId)
                    .active(false)
                    .build();

            when(contentReferenceRepository.findByContentIdAndOwnerTypeAndOwnerId(
                    contentId, OwnerType.DOCUMENT, ownerId))
                    .thenReturn(Optional.of(existing));
            when(contentReferenceRepository.save(existing)).thenReturn(existing);

            ContentReference result = service.attach(contentId, OwnerType.DOCUMENT, ownerId);

            assertTrue(result.isActive());
            verify(contentReferenceRepository).save(existing);
        }

        @Test
        @DisplayName("returns null when ledger is disabled")
        void returnsNullWhenDisabled() {
            ReflectionTestUtils.setField(service, "ledgerEnabled", false);

            ContentReference result = service.attach("content123", OwnerType.DOCUMENT, UUID.randomUUID());

            assertNull(result);
            verifyNoInteractions(contentReferenceRepository);
        }

        @Test
        @DisplayName("returns null for null or blank content ID")
        void returnsNullForBlankContentId() {
            assertNull(service.attach(null, OwnerType.DOCUMENT, UUID.randomUUID()));
            assertNull(service.attach("", OwnerType.DOCUMENT, UUID.randomUUID()));
            assertNull(service.attach("  ", OwnerType.DOCUMENT, UUID.randomUUID()));
            verifyNoInteractions(contentReferenceRepository);
        }
    }

    @Nested
    @DisplayName("detach")
    class Detach {

        @Test
        @DisplayName("deactivates reference without touching other owners")
        void deactivatesReferenceOnly() {
            String contentId = "20260413_abc123";
            UUID ownerId = UUID.randomUUID();

            when(contentReferenceRepository.deactivate(contentId, OwnerType.VERSION, ownerId))
                    .thenReturn(1);

            int count = service.detach(contentId, OwnerType.VERSION, ownerId);

            assertEquals(1, count);
            verify(contentReferenceRepository).deactivate(contentId, OwnerType.VERSION, ownerId);
        }

        @Test
        @DisplayName("returns 0 when reference does not exist")
        void returnsZeroWhenNotExists() {
            when(contentReferenceRepository.deactivate(anyString(), any(), any()))
                    .thenReturn(0);

            int count = service.detach("nonexistent", OwnerType.DOCUMENT, UUID.randomUUID());

            assertEquals(0, count);
        }

        @Test
        @DisplayName("skips when ledger disabled")
        void skipsWhenDisabled() {
            ReflectionTestUtils.setField(service, "ledgerEnabled", false);

            int count = service.detach("content123", OwnerType.DOCUMENT, UUID.randomUUID());

            assertEquals(0, count);
            verifyNoInteractions(contentReferenceRepository);
        }
    }

    @Nested
    @DisplayName("syncOwnerReference")
    class SyncOwnerReference {

        @Test
        @DisplayName("detaches previous content and attaches current content when content changes")
        void switchesOwnershipBetweenContentIds() {
            UUID ownerId = UUID.randomUUID();

            when(contentReferenceRepository.deactivate("old-content", OwnerType.DOCUMENT, ownerId))
                .thenReturn(1);
            when(contentReferenceRepository.findByContentIdAndOwnerTypeAndOwnerId(
                "new-content", OwnerType.DOCUMENT, ownerId
            )).thenReturn(Optional.empty());
            when(contentReferenceRepository.save(any(ContentReference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            service.syncOwnerReference("old-content", "new-content", OwnerType.DOCUMENT, ownerId);

            verify(contentReferenceRepository).deactivate("old-content", OwnerType.DOCUMENT, ownerId);
            verify(contentReferenceRepository).save(any(ContentReference.class));
        }

        @Test
        @DisplayName("reattaches current content when content id is unchanged")
        void ensuresReferenceExistsWhenContentUnchanged() {
            UUID ownerId = UUID.randomUUID();

            when(contentReferenceRepository.findByContentIdAndOwnerTypeAndOwnerId(
                "same-content", OwnerType.DOCUMENT, ownerId
            )).thenReturn(Optional.empty());
            when(contentReferenceRepository.save(any(ContentReference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            service.syncOwnerReference("same-content", "same-content", OwnerType.DOCUMENT, ownerId);

            verify(contentReferenceRepository, never()).deactivate(anyString(), any(), any());
            verify(contentReferenceRepository).save(any(ContentReference.class));
        }
    }

    @Nested
    @DisplayName("hasActiveReferences")
    class HasActiveReferences {

        @Test
        @DisplayName("returns true when active references exist")
        void returnsTrueWhenActive() {
            when(contentReferenceRepository.countByContentIdAndActiveTrue("content123"))
                    .thenReturn(2L);

            assertTrue(service.hasActiveReferences("content123"));
        }

        @Test
        @DisplayName("returns false when no active references")
        void returnsFalseWhenNone() {
            when(contentReferenceRepository.countByContentIdAndActiveTrue("content123"))
                    .thenReturn(0L);

            assertFalse(service.hasActiveReferences("content123"));
        }

        @Test
        @DisplayName("returns false for null content ID")
        void returnsFalseForNull() {
            assertFalse(service.hasActiveReferences(null));
            assertFalse(service.hasActiveReferences(""));
        }
    }

    @Nested
    @DisplayName("cleanupOrphanedContent")
    class OrphanCleanup {

        @BeforeEach
        void enableCleanup() {
            ReflectionTestUtils.setField(service, "orphanCleanupEnabled", true);
        }

        @Test
        @DisplayName("deletes orphaned content with zero active references")
        void deletesOrphanedContent() throws IOException {
            String orphanContentId = "20260413_orphan";
            when(contentReferenceRepository.findEligibleOrphanContentIds(any(LocalDateTime.class)))
                    .thenReturn(List.of(orphanContentId));
            when(contentReferenceRepository.countByContentIdAndActiveTrue(orphanContentId))
                    .thenReturn(0L);

            service.cleanupOrphanedContent();

            verify(contentService).deleteContent(orphanContentId);
            verify(contentReferenceRepository).purgeInactiveReferences(orphanContentId);
        }

        @Test
        @DisplayName("skips content that gained new references since query")
        void skipsContentWithNewReferences() throws IOException {
            String contentId = "20260413_revived";
            when(contentReferenceRepository.findEligibleOrphanContentIds(any(LocalDateTime.class)))
                    .thenReturn(List.of(contentId));
            when(contentReferenceRepository.countByContentIdAndActiveTrue(contentId))
                    .thenReturn(1L); // new reference appeared

            service.cleanupOrphanedContent();

            verify(contentService, never()).deleteContent(contentId);
        }

        @Test
        @DisplayName("does nothing when cleanup is disabled")
        void doesNothingWhenDisabled() {
            ReflectionTestUtils.setField(service, "orphanCleanupEnabled", false);

            service.cleanupOrphanedContent();

            verifyNoInteractions(contentReferenceRepository);
        }

        @Test
        @DisplayName("does nothing when no orphans found")
        void doesNothingWhenNoOrphans() throws IOException {
            when(contentReferenceRepository.findEligibleOrphanContentIds(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            service.cleanupOrphanedContent();

            verify(contentService, never()).deleteContent(anyString());
        }

        @Test
        @DisplayName("uses grace cutoff when selecting orphan candidates")
        void usesGraceCutoffWhenFindingOrphans() {
            ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            when(contentReferenceRepository.findEligibleOrphanContentIds(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

            LocalDateTime before = LocalDateTime.now().minusHours(24).minusMinutes(1);
            service.cleanupOrphanedContent();
            LocalDateTime after = LocalDateTime.now().minusHours(24).plusMinutes(1);

            verify(contentReferenceRepository).findEligibleOrphanContentIds(cutoffCaptor.capture());
            LocalDateTime cutoff = cutoffCaptor.getValue();
            assertTrue(!cutoff.isBefore(before) && !cutoff.isAfter(after));
        }
    }
}
