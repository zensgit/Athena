package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.entity.RenditionResource;
import com.ecm.core.entity.RenditionState;
import com.ecm.core.repository.RenditionResourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenditionResourceSyncServiceTest {

    private final RenditionDefinitionRegistry renditionDefinitionRegistry = new RenditionDefinitionRegistry();

    @Mock
    private RenditionResourceRepository renditionResourceRepository;

    @Test
    @DisplayName("Sync service mirrors ready preview and thumbnail state into rendition resources")
    void syncDocumentMirrorsReadyState() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(true);
        document.setThumbnailId("thumb-1");
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 26, 15, 0));
        document.setVersionLabel("2.0");

        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(documentId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RenditionResourceSyncService service = new RenditionResourceSyncService(
            renditionResourceRepository,
            renditionDefinitionRegistry
        );

        List<RenditionResource> resources = service.syncDocument(document);

        assertEquals(2, resources.size());
        assertEquals(RenditionState.READY, resources.get(0).getState());
        assertEquals(RenditionState.READY, resources.get(1).getState());
        assertEquals(RenditionDefinitionRegistry.GENERATION_MODE_PREVIEW_PIPELINE, resources.get(0).getGenerationMode());
        assertEquals(RenditionDefinitionRegistry.GENERATION_MODE_PREVIEW_DERIVED, resources.get(1).getGenerationMode());
        assertEquals("preview", resources.get(1).getDependencyRenditionKey());
        assertTrue(resources.get(1).isDownloadable());
    }

    @Test
    @DisplayName("Sync service keeps thumbnail registered when preview failed without thumbnail asset")
    void syncDocumentKeepsThumbnailRegisteredForFailedPreview() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewAvailable(false);
        document.setPreviewFailureReason("conversion failed");
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 26, 15, 30));

        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(documentId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RenditionResourceSyncService service = new RenditionResourceSyncService(
            renditionResourceRepository,
            renditionDefinitionRegistry
        );

        List<RenditionResource> resources = service.syncDocument(document);

        assertEquals(RenditionState.FAILED, resources.get(0).getState());
        assertEquals("conversion failed", resources.get(0).getErrorReason());
        assertEquals(RenditionState.REGISTERED, resources.get(1).getState());
        assertEquals(RenditionDefinitionRegistry.GENERATION_MODE_PREVIEW_DERIVED, resources.get(1).getGenerationMode());
        assertEquals("preview", resources.get(1).getDependencyRenditionKey());
    }

    @Test
    @DisplayName("Sync service projects generic binary preview as unsupported effective rendition state")
    void syncDocumentProjectsGenericBinaryAsUnsupported() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/octet-stream");

        when(renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(documentId)).thenReturn(List.of());
        when(renditionResourceRepository.save(any(RenditionResource.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RenditionResourceSyncService service = new RenditionResourceSyncService(
            renditionResourceRepository,
            renditionDefinitionRegistry
        );

        List<RenditionResource> resources = service.syncDocument(document);

        assertEquals(RenditionState.UNSUPPORTED, resources.get(0).getState());
        assertEquals("UNSUPPORTED", resources.get(0).getSourceStatus());
        assertEquals("Preview definition is not registered for generic binary sources", resources.get(0).getErrorReason());
        assertEquals("UNSUPPORTED", resources.get(0).getErrorCategory());
        assertEquals(RenditionState.REGISTERED, resources.get(1).getState());
        assertEquals("UNSUPPORTED", resources.get(1).getSourceStatus());
    }
}
