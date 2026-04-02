package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.entity.RenditionResource;
import com.ecm.core.entity.RenditionState;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.preview.PreviewService;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.RenditionResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenditionResourceServiceTest {

    private final RenditionDefinitionRegistry renditionDefinitionRegistry = new RenditionDefinitionRegistry();

    @Mock
    private NodeService nodeService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private RenditionResourceRepository renditionResourceRepository;

    @Mock
    private PreviewService previewService;

    @Mock
    private PreviewQueueService previewQueueService;

    private RenditionResourceService renditionResourceService;

    @BeforeEach
    void setup() {
        renditionResourceService = new RenditionResourceService(
            nodeService,
            documentRepository,
            new RenditionResourceSyncService(renditionResourceRepository, renditionDefinitionRegistry),
            renditionDefinitionRegistry,
            previewService,
            previewQueueService
        );
    }

    @Test
    @DisplayName("Document preview fields are mirrored into first-class rendition resources")
    void listForNodeBuildsAndPersistsMirroredResources() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setName("drawing.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(true);
        document.setThumbnailId("thumb-123");
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 26, 11, 0));
        document.setVersionLabel("1.2");

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<RenditionResource> resources = renditionResourceService.listForNode(nodeId);

        assertEquals(2, resources.size());
        assertEquals("preview", resources.get(0).getRenditionKey());
        assertEquals(RenditionState.READY, resources.get(0).getState());
        assertTrue(resources.get(0).isAvailable());
        assertEquals(RenditionDefinitionRegistry.GENERATION_MODE_PREVIEW_PIPELINE, resources.get(0).getGenerationMode());
        assertEquals("thumbnail", resources.get(1).getRenditionKey());
        assertEquals(RenditionState.READY, resources.get(1).getState());
        assertEquals(RenditionDefinitionRegistry.GENERATION_MODE_PREVIEW_DERIVED, resources.get(1).getGenerationMode());
        assertEquals("preview", resources.get(1).getDependencyRenditionKey());
        assertTrue(resources.get(1).isDownloadable());
        verify(renditionResourceRepository, times(2)).save(any(RenditionResource.class));
    }

    @Test
    @DisplayName("Created filter only returns non-registered rendition resources")
    void listForNodeFiltersCreatedResources() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(true);
        document.setVersionLabel("1.0");

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<RenditionResource> resources = renditionResourceService.listForNode(nodeId, "CREATED");

        assertEquals(1, resources.size());
        assertEquals("preview", resources.get(0).getRenditionKey());
        assertEquals(RenditionState.READY, resources.get(0).getState());
    }

    @Test
    @DisplayName("Exact state filter returns only matching rendition resource states")
    void listForNodeFiltersByExactState() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(true);
        document.setVersionLabel("1.0");

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<RenditionResource> resources = renditionResourceService.listForNode(nodeId, null, "READY");

        assertEquals(1, resources.size());
        assertEquals("preview", resources.get(0).getRenditionKey());
        assertEquals(RenditionState.READY, resources.get(0).getState());
    }

    @Test
    @DisplayName("Non-document nodes expose no rendition resources")
    void listForNodeReturnsEmptyForFolder() {
        UUID nodeId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(nodeId);
        folder.setName("workspace");

        when(nodeService.getNode(nodeId)).thenReturn(folder);

        List<RenditionResource> resources = renditionResourceService.listForNode(nodeId);

        assertTrue(resources.isEmpty());
    }

    @Test
    @DisplayName("Failed previews still expose registered thumbnail resources without pretending readiness")
    void listForNodeKeepsThumbnailRegisteredWhenOnlyPreviewFailed() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("LibreOffice conversion failed");
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 26, 9, 15));

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RenditionResource preview = renditionResourceService.getForNode(nodeId, "preview");
        RenditionResource thumbnail = renditionResourceService.getForNode(nodeId, "thumbnail");

        assertEquals(RenditionState.FAILED, preview.getState());
        assertEquals("LibreOffice conversion failed", preview.getErrorReason());
        assertEquals(RenditionState.REGISTERED, thumbnail.getState());
    }

    @Test
    @DisplayName("Unsupported generic binary source is exposed as not applicable instead of not-created")
    void listForNodeMarksGenericBinaryAsNotApplicable() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/octet-stream");

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<RenditionResource> allResources = renditionResourceService.listForNode(nodeId);
        List<RenditionResource> notCreatedResources = renditionResourceService.listForNode(nodeId, "NOT_CREATED");

        assertEquals(2, allResources.size());
        assertTrue(allResources.stream().noneMatch(RenditionResource::isApplicable));
        assertEquals(0, notCreatedResources.size());
        assertEquals(RenditionState.UNSUPPORTED, allResources.get(0).getState());
        assertEquals("UNSUPPORTED", allResources.get(0).getSourceStatus());
        assertEquals("UNSUPPORTED", allResources.get(0).getErrorCategory());
        assertEquals("Preview definition is not registered for generic binary sources", allResources.get(0).getErrorReason());
        assertEquals("Preview definition is not registered for generic binary sources", allResources.get(0).getApplicabilityReason());
        assertTrue(allResources.stream().allMatch(resource -> !resource.isApplicable()));
    }

    @Test
    @DisplayName("Definition registry exposes registered rendition metadata alongside current state")
    void listDefinitionsForNodeReturnsRegistryBackedStatuses() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(true);
        document.setVersionLabel("1.0");

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<RenditionResourceService.RenditionDefinitionStatus> definitions =
            renditionResourceService.listDefinitionsForNode(nodeId);

        assertEquals(2, definitions.size());
        assertEquals("preview", definitions.get(0).renditionKey());
        assertTrue(definitions.get(0).registered());
        assertTrue(definitions.get(0).applicable());
        assertEquals(RenditionDefinitionRegistry.GENERATION_MODE_PREVIEW_PIPELINE, definitions.get(0).generationMode());
        assertTrue(definitions.get(0).canRequeue());
        assertTrue(definitions.get(0).canInvalidate());
        assertEquals("thumbnail", definitions.get(1).renditionKey());
        assertEquals("preview", definitions.get(1).dependencyRenditionKey());
        assertEquals(RenditionDefinitionRegistry.GENERATION_MODE_PREVIEW_DERIVED, definitions.get(1).generationMode());
        assertEquals("REGISTERED", definitions.get(1).currentState());
        assertTrue(definitions.get(1).canRequeue());
        assertTrue(definitions.get(1).canInvalidate());
    }

    @Test
    @DisplayName("Summary uses preview rendition resource instead of raw document preview fields")
    void summarizeDocumentUsesPreviewRenditionResource() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(false);
        document.setPreviewFailureReason("stale flag");
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 28, 9, 45));
        document.setVersionLabel("3.2");

        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RenditionResourceService.RenditionSummary summary = renditionResourceService.summarizeDocument(document);

        assertEquals(nodeId, summary.nodeId());
        assertEquals("READY", summary.previewStatus());
        assertTrue(summary.document());
        assertTrue(summary.renditionAvailable());
        assertEquals("3.2", summary.currentVersionLabel());
    }

    @Test
    @DisplayName("Summary exposes unsupported effective preview semantics for generic binary sources")
    void summarizeDocumentExposesUnsupportedEffectivePreviewSemantics() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/octet-stream");

        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RenditionResourceService.RenditionSummary summary = renditionResourceService.summarizeDocument(document);

        assertEquals(nodeId, summary.nodeId());
        assertEquals("UNSUPPORTED", summary.previewStatus());
        assertEquals("UNSUPPORTED", summary.previewFailureCategory());
        assertEquals("Preview definition is not registered for generic binary sources", summary.previewFailureReason());
        assertTrue(summary.document());
        assertFalse(summary.renditionAvailable());
    }

    @Test
    @DisplayName("Preview requeue returns refreshed rendition resource and effective preview summary")
    void requeuePreviewReturnsEffectivePreviewSummary() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("LibreOffice conversion failed");
        document.setPreviewAvailable(false);
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 28, 18, 10));

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(previewQueueService.enqueue(nodeId, true))
            .thenAnswer(invocation -> {
                document.setPreviewStatus(PreviewStatus.PROCESSING);
                document.setPreviewFailureReason(null);
                document.setPreviewAvailable(false);
                document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 28, 18, 12));
                return new PreviewQueueService.PreviewQueueStatus(
                    nodeId,
                    PreviewStatus.FAILED,
                    true,
                    1,
                    null,
                    "Preview queued"
                );
            });
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RenditionResourceService.RenditionMutationResult result = renditionResourceService.requeueForNode(
            nodeId,
            "preview",
            true
        );

        assertEquals("REQUEUE", result.action());
        assertFalse(result.invalidated());
        assertEquals(RenditionState.PROCESSING, result.resource().getState());
        assertNotNull(result.previewSummary());
        assertEquals("PROCESSING", result.previewSummary().previewStatus());
        assertEquals(nodeId, result.previewSummary().nodeId());
        verify(previewQueueService).enqueue(nodeId, true);
    }

    @Test
    @DisplayName("Preview requeue falls back to queue status when rendition summary is unavailable")
    void requeuePreviewFallsBackToQueueStatusSummaryWhenRenditionSummaryUnavailable() {
        UUID nodeId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 29, 20, 15);

        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setVersionLabel("4.1");

        RenditionResourceService service = spy(renditionResourceService);

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(previewQueueService.enqueue(nodeId, true))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                nodeId,
                PreviewStatus.PROCESSING,
                true,
                1,
                null,
                "Preview queued",
                null,
                null,
                updatedAt
            ));
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(RenditionResourceService.RenditionSummary.empty(nodeId))
            .when(service).summarizeDocument(document);

        RenditionResourceService.RenditionMutationResult result = service.requeueForNode(
            nodeId,
            "preview",
            true
        );

        assertNotNull(result.previewSummary());
        assertEquals("PROCESSING", result.previewSummary().previewStatus());
        assertEquals(updatedAt, result.previewSummary().previewLastUpdated());
        assertEquals("4.1", result.previewSummary().currentVersionLabel());
    }

    @Test
    @DisplayName("Shared preview mutation summary falls back to queue status when rendition summary is unavailable")
    void resolvePreviewMutationSummaryFallsBackToQueueStatusWhenSummaryUnavailable() {
        UUID nodeId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 29, 20, 45);

        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setVersionLabel("4.2");

        RenditionResourceService service = spy(renditionResourceService);
        PreviewQueueService.PreviewQueueStatus queueStatus = new PreviewQueueService.PreviewQueueStatus(
            nodeId,
            PreviewStatus.PROCESSING,
            true,
            2,
            null,
            "Preview queued",
            "Waiting on renderer",
            "TEMPORARY",
            updatedAt
        );

        doReturn(RenditionResourceService.RenditionSummary.empty(nodeId))
            .when(service).summarizeDocument(document);

        RenditionResourceService.RenditionSummary summary =
            service.resolvePreviewMutationSummary(document, queueStatus);

        assertEquals(nodeId, summary.nodeId());
        assertTrue(summary.document());
        assertEquals("PROCESSING", summary.previewStatus());
        assertEquals("Waiting on renderer", summary.previewFailureReason());
        assertEquals("TEMPORARY", summary.previewFailureCategory());
        assertEquals(updatedAt, summary.previewLastUpdated());
        assertEquals("4.2", summary.currentVersionLabel());
        assertFalse(summary.renditionAvailable());
    }

    @Test
    @DisplayName("Shared effective preview snapshot falls back to document semantics when rendition summary is unavailable")
    void resolveEffectivePreviewSnapshotFallsBackToDocumentSemanticsWhenSummaryUnavailable() {
        UUID nodeId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 30, 10, 15);

        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason(null);
        document.setPreviewLastUpdated(updatedAt);

        RenditionResourceService service = spy(renditionResourceService);
        doReturn(RenditionResourceService.RenditionSummary.empty(nodeId))
            .when(service).summarizeDocument(document);

        RenditionResourceService.EffectivePreviewSnapshot snapshot =
            service.resolveEffectivePreviewSnapshot(document, null, null, null, null);

        assertNotNull(snapshot);
        assertEquals("UNSUPPORTED", snapshot.previewStatus());
        assertEquals("UNSUPPORTED", snapshot.previewFailureCategory());
        assertNotNull(snapshot.previewFailureReason());
        assertEquals(updatedAt, snapshot.previewLastUpdated());
    }

    @Test
    @DisplayName("Shared effective preview snapshot uses explicit fallback when document is missing")
    void resolveEffectivePreviewSnapshotUsesExplicitFallbackWhenDocumentMissing() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 30, 10, 45);

        RenditionResourceService.EffectivePreviewSnapshot snapshot =
            renditionResourceService.resolveEffectivePreviewSnapshot(
                null,
                "processing",
                "  waiting on renderer  ",
                "temporary",
                updatedAt
            );

        assertNotNull(snapshot);
        assertEquals("PROCESSING", snapshot.previewStatus());
        assertEquals("waiting on renderer", snapshot.previewFailureReason());
        assertEquals("TEMPORARY", snapshot.previewFailureCategory());
        assertEquals(updatedAt, snapshot.previewLastUpdated());
    }

    @Test
    @DisplayName("Shared preview mutation status prefers preview summary over raw queue status")
    void resolvePreviewMutationStatusPrefersPreviewSummary() {
        UUID nodeId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 30, 11, 0);

        RenditionResourceService.PreviewMutationStatus status =
            renditionResourceService.resolvePreviewMutationStatus(
                new RenditionResourceService.RenditionSummary(
                    nodeId,
                    true,
                    "UNSUPPORTED",
                    false,
                    "Preview definition is not registered for generic binary sources",
                    "UNSUPPORTED",
                    updatedAt,
                    "7.2"
                ),
                new PreviewQueueService.PreviewQueueStatus(
                    nodeId,
                    PreviewStatus.FAILED,
                    true,
                    3,
                    null,
                    "Preview queued",
                    "legacy failed",
                    "TEMPORARY",
                    LocalDateTime.of(2026, 3, 30, 10, 0)
                )
            );

        assertEquals(nodeId, status.documentId());
        assertEquals("UNSUPPORTED", status.previewStatus());
        assertEquals("Preview definition is not registered for generic binary sources", status.previewFailureReason());
        assertEquals("UNSUPPORTED", status.previewFailureCategory());
        assertEquals(updatedAt, status.previewLastUpdated());
        assertTrue(status.queued());
        assertEquals(3, status.attempts());
        assertEquals("Preview queued", status.message());
    }

    @Test
    @DisplayName("Shared preview mutation status falls back to queue status when preview summary is empty")
    void resolvePreviewMutationStatusFallsBackToQueueStatusWhenPreviewSummaryEmpty() {
        UUID nodeId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 30, 11, 30);

        RenditionResourceService.PreviewMutationStatus status =
            renditionResourceService.resolvePreviewMutationStatus(
                RenditionResourceService.RenditionSummary.empty(nodeId),
                new PreviewQueueService.PreviewQueueStatus(
                    nodeId,
                    PreviewStatus.PROCESSING,
                    true,
                    1,
                    null,
                    "Preview queued",
                    null,
                    null,
                    updatedAt
                )
            );

        assertEquals(nodeId, status.documentId());
        assertEquals("PROCESSING", status.previewStatus());
        assertNull(status.previewFailureReason());
        assertNull(status.previewFailureCategory());
        assertEquals(updatedAt, status.previewLastUpdated());
        assertTrue(status.queued());
        assertEquals(1, status.attempts());
    }

    @Test
    @DisplayName("Preview invalidation clears thumbnail marker and can requeue preview-linked generation")
    void invalidatePreviewClearsThumbnailAndQueuesRepair() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(true);
        document.setThumbnailId("thumb-123");
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 26, 12, 0));

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.invalidateRendition(document, "stale preview"))
            .thenAnswer(invocation -> {
                document.setPreviewStatus(PreviewStatus.FAILED);
                document.setPreviewAvailable(false);
                return new PreviewService.PreviewRepairResult(
                    nodeId,
                    "READY",
                    true,
                    "stale preview",
                    "hash-a",
                    "hash-b"
                );
            });
        when(previewQueueService.enqueue(nodeId, true))
            .thenAnswer(invocation -> {
                document.setPreviewStatus(PreviewStatus.PROCESSING);
                document.setPreviewAvailable(false);
                return new PreviewQueueService.PreviewQueueStatus(nodeId, PreviewStatus.FAILED, true, 0, null, "Preview queued");
            });
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RenditionResourceService.RenditionMutationResult result = renditionResourceService.invalidateForNode(
            nodeId,
            "preview",
            "stale preview",
            true,
            true
        );

        assertTrue(result.invalidated());
        assertEquals("INVALIDATE", result.action());
        assertEquals("preview", result.renditionKey());
        assertEquals(RenditionState.PROCESSING, result.resource().getState());
        assertNotNull(result.previewSummary());
        assertEquals("PROCESSING", result.previewSummary().previewStatus());
        verify(documentRepository).save(any(Document.class));
        verify(previewService).invalidateRendition(document, "stale preview");
        verify(previewQueueService).enqueue(nodeId, true);
    }

    @Test
    @DisplayName("Thumbnail invalidation only clears thumbnail marker without mutating preview state")
    void invalidateThumbnailDoesNotCallPreviewInvalidate() {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(true);
        document.setThumbnailId("thumb-123");
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 26, 12, 15));

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(nodeId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RenditionResourceService.RenditionMutationResult result = renditionResourceService.invalidateForNode(
            nodeId,
            "thumbnail",
            null,
            false,
            true
        );

        assertTrue(result.invalidated());
        assertEquals("thumbnail", result.renditionKey());
        assertEquals(RenditionState.REGISTERED, result.resource().getState());
        assertNotNull(result.previewSummary());
        assertEquals("READY", result.previewSummary().previewStatus());
        verify(previewService, never()).invalidateRendition(any(Document.class), any());
        verify(previewQueueService, never()).enqueue(eq(nodeId), anyBoolean());
    }
}
