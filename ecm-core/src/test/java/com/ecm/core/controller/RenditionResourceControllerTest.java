package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.RenditionResource;
import com.ecm.core.entity.RenditionState;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.service.RenditionResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RenditionResourceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RenditionResourceService renditionResourceService;

    @InjectMocks
    private RenditionResourceController renditionResourceController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(renditionResourceController).build();
    }

    @Test
    @DisplayName("Node rendition resources list exposes ready and registered resources")
    void listNodeRenditionsReturnsFirstClassResources() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);

        when(renditionResourceService.listForNode(nodeId, null, null)).thenReturn(List.of(
            RenditionResource.builder()
                .id(UUID.randomUUID())
                .document(document)
                .renditionKey("preview")
                .label("Preview")
                .mimeType("application/json")
                .state(RenditionState.READY)
                .available(true)
                .downloadable(false)
                .applicable(true)
                .generationMode("PREVIEW_PIPELINE")
                .contentUrl("/api/v1/documents/" + nodeId + "/preview")
                .sourceStatus("READY")
                .versionLabel("1.0")
                .sourceUpdatedAt(LocalDateTime.of(2026, 3, 26, 10, 30))
                .lastSyncedAt(LocalDateTime.of(2026, 3, 26, 10, 31))
                .sortOrder(0)
                .build(),
            RenditionResource.builder()
                .id(UUID.randomUUID())
                .document(document)
                .renditionKey("thumbnail")
                .label("Thumbnail")
                .mimeType("image/png")
                .state(RenditionState.REGISTERED)
                .available(false)
                .downloadable(true)
                .applicable(true)
                .generationMode("PREVIEW_DERIVED")
                .dependencyRenditionKey("preview")
                .contentUrl("/api/v1/documents/" + nodeId + "/thumbnail")
                .sourceStatus("READY")
                .versionLabel("1.0")
                .sourceUpdatedAt(LocalDateTime.of(2026, 3, 26, 10, 30))
                .lastSyncedAt(LocalDateTime.of(2026, 3, 26, 10, 31))
                .sortOrder(1)
                .build()
        ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/renditions", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].renditionKey").value("preview"))
            .andExpect(jsonPath("$[0].state").value("READY"))
            .andExpect(jsonPath("$[0].available").value(true))
            .andExpect(jsonPath("$[0].applicable").value(true))
            .andExpect(jsonPath("$[0].generationMode").value("PREVIEW_PIPELINE"))
            .andExpect(jsonPath("$[1].renditionKey").value("thumbnail"))
            .andExpect(jsonPath("$[1].state").value("REGISTERED"))
            .andExpect(jsonPath("$[1].generationMode").value("PREVIEW_DERIVED"))
            .andExpect(jsonPath("$[1].dependencyRenditionKey").value("preview"))
            .andExpect(jsonPath("$[1].downloadable").value(true));
    }

    @Test
    @DisplayName("Node rendition resources list forwards created filter")
    void listNodeRenditionsSupportsStatusFilter() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);

        when(renditionResourceService.listForNode(nodeId, "CREATED", "READY")).thenReturn(List.of(
            RenditionResource.builder()
                .id(UUID.randomUUID())
                .document(document)
                .renditionKey("preview")
                .label("Preview")
                .mimeType("application/json")
                .state(RenditionState.READY)
                .available(true)
                .downloadable(false)
                .applicable(true)
                .generationMode("PREVIEW_PIPELINE")
                .contentUrl("/api/v1/documents/" + nodeId + "/preview")
                .sourceStatus("READY")
                .lastSyncedAt(LocalDateTime.of(2026, 3, 26, 11, 1))
                .sortOrder(0)
                .build()
        ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/renditions", nodeId)
                .param("status", "CREATED")
                .param("state", "READY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].renditionKey").value("preview"));
    }

    @Test
    @DisplayName("Single rendition resource endpoint returns one first-class rendition")
    void getNodeRenditionReturnsSingleResource() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        UUID renditionId = UUID.randomUUID();

        when(renditionResourceService.getForNode(nodeId, "preview")).thenReturn(
            RenditionResource.builder()
                .id(renditionId)
                .document(document)
                .renditionKey("preview")
                .label("Preview")
                .mimeType("application/json")
                .state(RenditionState.PROCESSING)
                .available(false)
                .downloadable(false)
                .applicable(true)
                .generationMode("PREVIEW_PIPELINE")
                .contentUrl("/api/v1/documents/" + nodeId + "/preview")
                .sourceStatus("PROCESSING")
                .lastSyncedAt(LocalDateTime.of(2026, 3, 26, 10, 31))
                .sortOrder(0)
                .build()
        );

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/renditions/{renditionKey}", nodeId, "preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(renditionId.toString()))
            .andExpect(jsonPath("$.renditionKey").value("preview"))
            .andExpect(jsonPath("$.state").value("PROCESSING"))
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.generationMode").value("PREVIEW_PIPELINE"))
            .andExpect(jsonPath("$.applicable").value(true));
    }

    @Test
    @DisplayName("List endpoint exposes not-applicable rendition metadata")
    void listNodeRenditionsExposesApplicabilityMetadata() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);

        when(renditionResourceService.listForNode(nodeId, null, null)).thenReturn(List.of(
            RenditionResource.builder()
                .id(UUID.randomUUID())
                .document(document)
                .renditionKey("preview")
                .label("Preview")
                .mimeType("application/json")
                .state(RenditionState.REGISTERED)
                .available(false)
                .downloadable(false)
                .applicable(false)
                .applicabilityReason("Preview generation is not applicable for this source content")
                .generationMode("PREVIEW_PIPELINE")
                .contentUrl("/api/v1/documents/" + nodeId + "/preview")
                .sourceStatus(null)
                .lastSyncedAt(LocalDateTime.of(2026, 3, 28, 12, 1))
                .sortOrder(0)
                .build()
        ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/renditions", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].applicable").value(false))
            .andExpect(jsonPath("$[0].generationMode").value("PREVIEW_PIPELINE"))
            .andExpect(jsonPath("$[0].applicabilityReason").value("Preview generation is not applicable for this source content"));
    }

    @Test
    @DisplayName("Definition list endpoint exposes registry-backed rendition metadata")
    void listNodeRenditionDefinitionsReturnsRegistryMetadata() throws Exception {
        UUID nodeId = UUID.randomUUID();

        when(renditionResourceService.listDefinitionsForNode(nodeId)).thenReturn(List.of(
            new RenditionResourceService.RenditionDefinitionStatus(
                nodeId,
                "preview",
                "Preview",
                "application/json",
                "PREVIEW_PIPELINE",
                false,
                0,
                null,
                true,
                false,
                "Preview definition requires a known source mime type",
                "REGISTERED",
                false,
                "/api/v1/documents/" + nodeId + "/preview",
                false,
                false,
                "Preview definition requires a known source mime type"
            ),
            new RenditionResourceService.RenditionDefinitionStatus(
                nodeId,
                "thumbnail",
                "Thumbnail",
                "image/png",
                "PREVIEW_DERIVED",
                true,
                1,
                "preview",
                true,
                false,
                "Thumbnail definition depends on a preview-eligible source rendition",
                "REGISTERED",
                false,
                "/api/v1/documents/" + nodeId + "/thumbnail",
                false,
                false,
                "Thumbnail definition depends on a preview-eligible source rendition"
            )
        ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/renditions/definitions", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].renditionKey").value("preview"))
            .andExpect(jsonPath("$[0].registered").value(true))
            .andExpect(jsonPath("$[0].applicable").value(false))
            .andExpect(jsonPath("$[0].generationMode").value("PREVIEW_PIPELINE"))
            .andExpect(jsonPath("$[0].canRequeue").value(false))
            .andExpect(jsonPath("$[0].canInvalidate").value(false))
            .andExpect(jsonPath("$[1].renditionKey").value("thumbnail"))
            .andExpect(jsonPath("$[1].dependencyRenditionKey").value("preview"))
            .andExpect(jsonPath("$[1].currentState").value("REGISTERED"))
            .andExpect(jsonPath("$[1].mutationBlockedReason").value("Thumbnail definition depends on a preview-eligible source rendition"));
    }

    @Test
    @DisplayName("Requeue rendition endpoint exposes queue status and refreshed resource")
    void requeueNodeRenditionReturnsMutationEnvelope() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);

        when(renditionResourceService.requeueForNode(nodeId, "preview", true)).thenReturn(
            new RenditionResourceService.RenditionMutationResult(
                "preview",
                "REQUEUE",
                false,
                true,
                "Queued preview-linked rendition pipeline",
                new PreviewQueueService.PreviewQueueStatus(
                    nodeId,
                    PreviewStatus.FAILED,
                    true,
                    0,
                    null,
                    "Preview queued"
                ),
                RenditionResource.builder()
                    .id(UUID.randomUUID())
                    .document(document)
                    .renditionKey("preview")
                    .label("Preview")
                    .mimeType("application/json")
                    .state(RenditionState.PROCESSING)
                    .available(false)
                    .downloadable(false)
                    .applicable(true)
                    .generationMode("PREVIEW_PIPELINE")
                    .contentUrl("/api/v1/documents/" + nodeId + "/preview")
                    .sourceStatus("PROCESSING")
                    .lastSyncedAt(LocalDateTime.of(2026, 3, 26, 11, 5))
                    .sortOrder(0)
                    .build(),
                new RenditionResourceService.RenditionSummary(
                    nodeId,
                    true,
                    "PROCESSING",
                    false,
                    null,
                    null,
                    LocalDateTime.of(2026, 3, 26, 11, 5),
                    "1.4"
                )
            )
        );
        when(renditionResourceService.resolvePreviewMutationStatus(
            org.mockito.ArgumentMatchers.any(RenditionResourceService.RenditionSummary.class),
            org.mockito.ArgumentMatchers.any(PreviewQueueService.PreviewQueueStatus.class)
        )).thenReturn(
            new RenditionResourceService.PreviewMutationStatus(
                nodeId,
                "PROCESSING",
                null,
                null,
                LocalDateTime.of(2026, 3, 26, 11, 5),
                true,
                0,
                null,
                "Preview queued"
            )
        );

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/renditions/{renditionKey}/requeue", nodeId, "preview")
                .param("force", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.action").value("REQUEUE"))
            .andExpect(jsonPath("$.invalidated").value(false))
            .andExpect(jsonPath("$.queueStatus.previewStatus").value("PROCESSING"))
            .andExpect(jsonPath("$.queueStatus.queued").value(true))
            .andExpect(jsonPath("$.previewSummary.previewStatus").value("PROCESSING"))
            .andExpect(jsonPath("$.previewSummary.currentVersionLabel").value("1.4"))
            .andExpect(jsonPath("$.resource.generationMode").value("PREVIEW_PIPELINE"))
            .andExpect(jsonPath("$.resource.state").value("PROCESSING"));
    }

    @Test
    @DisplayName("Invalidate rendition endpoint exposes invalidation result and refreshed resource")
    void invalidateNodeRenditionReturnsMutationEnvelope() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);

        when(renditionResourceService.invalidateForNode(nodeId, "thumbnail", "manual reset", true, true)).thenReturn(
            new RenditionResourceService.RenditionMutationResult(
                "thumbnail",
                "INVALIDATE",
                true,
                true,
                "Invalidated preview-linked rendition state",
                new PreviewQueueService.PreviewQueueStatus(
                    nodeId,
                    PreviewStatus.READY,
                    true,
                    0,
                    null,
                    "Preview queued"
                ),
                RenditionResource.builder()
                    .id(UUID.randomUUID())
                    .document(document)
                    .renditionKey("thumbnail")
                    .label("Thumbnail")
                    .mimeType("image/png")
                    .state(RenditionState.PROCESSING)
                    .available(false)
                    .downloadable(true)
                    .applicable(true)
                    .generationMode("PREVIEW_DERIVED")
                    .dependencyRenditionKey("preview")
                    .contentUrl("/api/v1/documents/" + nodeId + "/thumbnail")
                    .sourceStatus("PROCESSING")
                    .lastSyncedAt(LocalDateTime.of(2026, 3, 26, 11, 6))
                    .sortOrder(1)
                    .build(),
                new RenditionResourceService.RenditionSummary(
                    nodeId,
                    true,
                    "PROCESSING",
                    false,
                    null,
                    null,
                    LocalDateTime.of(2026, 3, 26, 11, 6),
                    "1.4"
                )
            )
        );
        when(renditionResourceService.resolvePreviewMutationStatus(
            org.mockito.ArgumentMatchers.any(RenditionResourceService.RenditionSummary.class),
            org.mockito.ArgumentMatchers.any(PreviewQueueService.PreviewQueueStatus.class)
        )).thenReturn(
            new RenditionResourceService.PreviewMutationStatus(
                nodeId,
                "PROCESSING",
                null,
                null,
                LocalDateTime.of(2026, 3, 26, 11, 6),
                true,
                0,
                null,
                "Preview queued"
            )
        );

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/renditions/{renditionKey}/invalidate", nodeId, "thumbnail")
                .param("reason", "manual reset")
                .param("requeue", "true")
                .param("forceQueue", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.action").value("INVALIDATE"))
            .andExpect(jsonPath("$.invalidated").value(true))
            .andExpect(jsonPath("$.queueStatus.previewStatus").value("PROCESSING"))
            .andExpect(jsonPath("$.previewSummary.previewStatus").value("PROCESSING"))
            .andExpect(jsonPath("$.resource.dependencyRenditionKey").value("preview"))
            .andExpect(jsonPath("$.resource.renditionKey").value("thumbnail"));
    }
}
