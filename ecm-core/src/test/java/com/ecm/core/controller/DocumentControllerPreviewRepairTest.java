package com.ecm.core.controller;

import com.ecm.core.conversion.ConversionService;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.ocr.OcrQueueService;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.preview.PreviewService;
import com.ecm.core.service.CheckOutCheckInService;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.PdfAnnotationService;
import com.ecm.core.service.RenditionResourceService;
import com.ecm.core.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerPreviewRepairTest {

    private MockMvc mockMvc;

    @Mock
    private NodeService nodeService;

    @Mock
    private VersionService versionService;

    @Mock
    private ContentService contentService;

    @Mock
    private PreviewService previewService;

    @Mock
    private PreviewQueueService previewQueueService;

    @Mock
    private OcrQueueService ocrQueueService;

    @Mock
    private ConversionService conversionService;

    @Mock
    private PdfAnnotationService pdfAnnotationService;

    @Mock
    private RenditionResourceService renditionResourceService;

    @Mock
    private CheckOutCheckInService checkOutCheckInService;

    @BeforeEach
    void setUp() {
        DocumentController controller = new DocumentController(
            nodeService,
            versionService,
            contentService,
            previewService,
            previewQueueService,
            ocrQueueService,
            conversionService,
            pdfAnnotationService,
            renditionResourceService,
            checkOutCheckInService
        );
        ReflectionTestUtils.setField(controller, "previewReadHashEnforceEnabled", true);
        ReflectionTestUtils.setField(controller, "previewReadAutoRepairOnStale", true);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Preview read path enforces hash check and triggers auto-repair queue")
    void previewReadPathShouldEnforceHashAndAutoRepair() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("contract.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setContentHash("hash-new");
        document.setPreviewContentHash("hash-old");
        document.setFileSize(1024L);

        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(previewService.evaluateReadiness(document))
            .thenReturn(new PreviewService.PreviewReadiness("READY_STALE_HASH", false, true, "STALE_HASH_MISMATCH"));
        Mockito.when(previewService.invalidateRendition(document, "STALE_HASH_MISMATCH"))
            .thenReturn(new PreviewService.PreviewRepairResult(
                documentId,
                "READY",
                true,
                "STALE_HASH_MISMATCH",
                "hash-old",
                "hash-new"
            ));
        PreviewQueueService.PreviewQueueStatus queueStatus = new PreviewQueueService.PreviewQueueStatus(
            documentId,
            PreviewStatus.FAILED,
            true,
            0,
            null,
            "Preview queued"
        );
        Mockito.when(previewQueueService.enqueue(documentId, true)).thenReturn(queueStatus);
        Mockito.when(renditionResourceService.resolvePreviewMutationSummary(document, queueStatus))
            .thenReturn(RenditionResourceService.RenditionSummary.empty(documentId));

        mockMvc.perform(get("/api/v1/documents/{documentId}/preview", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.supported").value(false))
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failureReason").value("STALE_HASH_MISMATCH"))
            .andExpect(jsonPath("$.failureCategory").value("TEMPORARY"))
            .andExpect(jsonPath("$.retryNeeded").value(true))
            .andExpect(jsonPath("$.message").value("Preview withheld by hash enforcement; auto-repair queued"));

        Mockito.verify(previewService, Mockito.never()).generatePreview(Mockito.any());
        Mockito.verify(previewQueueService).enqueue(documentId, true);
    }

    @Test
    @DisplayName("Preview read path treats zero-source hash enforcement as unsupported")
    void previewReadPathShouldReturnUnsupportedForZeroSource() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("empty.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setContentHash("hash-zero");
        document.setPreviewContentHash("hash-zero");
        document.setFileSize(0L);

        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(previewService.evaluateReadiness(document))
            .thenReturn(new PreviewService.PreviewReadiness("ZERO_SOURCE", false, true, "SOURCE_EMPTY"));
        Mockito.when(previewService.invalidateRendition(document, "SOURCE_EMPTY"))
            .thenReturn(new PreviewService.PreviewRepairResult(
                documentId,
                "READY",
                true,
                "SOURCE_EMPTY",
                "hash-zero",
                "hash-zero"
            ));
        Mockito.when(renditionResourceService.resolvePreviewMutationSummary(document, null))
            .thenReturn(RenditionResourceService.RenditionSummary.empty(documentId));

        mockMvc.perform(get("/api/v1/documents/{documentId}/preview", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.supported").value(false))
            .andExpect(jsonPath("$.status").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.failureCategory").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.retryNeeded").value(false))
            .andExpect(jsonPath("$.message").value("Preview withheld: source content is empty"));

        Mockito.verify(previewQueueService, Mockito.never()).enqueue(Mockito.any(), Mockito.anyBoolean());
    }

    @Test
    @DisplayName("Manual preview repair endpoint invalidates and requeues")
    void repairPreviewShouldInvalidateAndRequeue() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("report.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setContentHash("hash-a");
        document.setPreviewContentHash("hash-b");
        document.setFileSize(4096L);

        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(previewService.evaluateReadiness(document))
            .thenReturn(new PreviewService.PreviewReadiness("READY_STALE_HASH", false, true, "STALE_HASH_MISMATCH"));
        Mockito.when(previewService.invalidateRendition(document, "STALE_HASH_MISMATCH"))
            .thenReturn(new PreviewService.PreviewRepairResult(
                documentId,
                "READY",
                true,
                "STALE_HASH_MISMATCH",
                "hash-b",
                "hash-a"
            ));
        PreviewQueueService.PreviewQueueStatus queueStatus = new PreviewQueueService.PreviewQueueStatus(
            documentId,
            PreviewStatus.FAILED,
            true,
            0,
            null,
            "Preview queued"
        );
        Mockito.when(previewQueueService.enqueue(documentId, true)).thenReturn(queueStatus);
        Mockito.when(renditionResourceService.resolvePreviewMutationStatus(document, queueStatus))
            .thenReturn(new RenditionResourceService.PreviewMutationStatus(
                documentId,
                null,
                null,
                null,
                null,
                true,
                0,
                null,
                "Preview queued"
            ));

        mockMvc.perform(post("/api/v1/documents/{documentId}/preview/repair", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.readinessState").value("READY_STALE_HASH"))
            .andExpect(jsonPath("$.invalidated").value(true))
            .andExpect(jsonPath("$.queued").value(true))
            .andExpect(jsonPath("$.queueMessage").value("Preview queued"));
    }

    @Test
    @DisplayName("Preview read hash-enforced response prefers rendition-backed effective summary")
    void previewReadPathPrefersRenditionSummaryForHashEnforcedResponse() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("blob.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("legacy failed");
        document.setContentHash("hash-new");
        document.setPreviewContentHash("hash-old");
        document.setFileSize(512L);

        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(previewService.evaluateReadiness(document))
            .thenReturn(new PreviewService.PreviewReadiness("READY_STALE_HASH", false, true, "STALE_HASH_MISMATCH"));
        Mockito.when(previewService.invalidateRendition(document, "STALE_HASH_MISMATCH"))
            .thenReturn(new PreviewService.PreviewRepairResult(
                documentId,
                "FAILED",
                true,
                "STALE_HASH_MISMATCH",
                "hash-old",
                "hash-new"
            ));
        PreviewQueueService.PreviewQueueStatus queueStatus = new PreviewQueueService.PreviewQueueStatus(
            documentId,
            PreviewStatus.FAILED,
            true,
            0,
            null,
            "Preview queued"
        );
        Mockito.when(previewQueueService.enqueue(documentId, true)).thenReturn(queueStatus);
        Mockito.when(renditionResourceService.resolvePreviewMutationSummary(document, queueStatus)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                LocalDateTime.of(2026, 3, 29, 9, 0),
                "1.0"
            )
        );

        mockMvc.perform(get("/api/v1/documents/{documentId}/preview", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.failureCategory").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.failureReason")
                .value("Preview definition is not registered for generic binary sources"));
    }

    @Test
    @DisplayName("Queue preview response exposes rendition-backed preview summary")
    void queuePreviewReturnsRenditionBackedSummary() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("blob.bin");
        document.setMimeType("application/octet-stream");

        PreviewQueueService.PreviewQueueStatus queueStatus = new PreviewQueueService.PreviewQueueStatus(
            documentId,
            PreviewStatus.FAILED,
            true,
            2,
            null,
            "Preview queued"
        );
        Mockito.when(previewQueueService.enqueue(documentId, true)).thenReturn(queueStatus);
        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(renditionResourceService.resolvePreviewMutationStatus(document, queueStatus)).thenReturn(
            new RenditionResourceService.PreviewMutationStatus(
                documentId,
                "UNSUPPORTED",
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                null,
                true,
                2,
                null,
                "Preview queued"
            )
        );

        mockMvc.perform(post("/api/v1/documents/{documentId}/preview/queue", documentId)
                .param("force", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.queued").value(true))
            .andExpect(jsonPath("$.previewStatus").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.previewFailureCategory").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.previewFailureReason")
                .value("Preview definition is not registered for generic binary sources"));
    }

    @Test
    @DisplayName("Queue preview response falls back to queue status effective summary when rendition summary is unavailable")
    void queuePreviewFallsBackToQueueStatusEffectiveSummary() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("queued.pdf");
        document.setMimeType("application/pdf");

        PreviewQueueService.PreviewQueueStatus queueStatus = new PreviewQueueService.PreviewQueueStatus(
            documentId,
            PreviewStatus.PROCESSING,
            true,
            1,
            null,
            "Preview queued",
            null,
            null,
            LocalDateTime.of(2026, 3, 29, 12, 30)
        );
        Mockito.when(previewQueueService.enqueue(documentId, true)).thenReturn(queueStatus);
        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(renditionResourceService.resolvePreviewMutationStatus(document, queueStatus))
            .thenReturn(new RenditionResourceService.PreviewMutationStatus(
                documentId,
                "PROCESSING",
                null,
                null,
                LocalDateTime.of(2026, 3, 29, 12, 30),
                true,
                1,
                null,
                "Preview queued"
            ));

        mockMvc.perform(post("/api/v1/documents/{documentId}/preview/queue", documentId)
                .param("force", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.queued").value(true))
            .andExpect(jsonPath("$.previewStatus").value("PROCESSING"))
            .andExpect(jsonPath("$.previewLastUpdated").exists());
    }

    @Test
    @DisplayName("Repair preview response exposes rendition-backed preview summary")
    void repairPreviewReturnsRenditionBackedSummary() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("blob.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("legacy failed");
        document.setContentHash("hash-a");
        document.setPreviewContentHash("hash-b");
        document.setFileSize(2048L);

        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(previewService.evaluateReadiness(document))
            .thenReturn(new PreviewService.PreviewReadiness("READY_STALE_HASH", false, true, "STALE_HASH_MISMATCH"));
        Mockito.when(previewService.invalidateRendition(document, "STALE_HASH_MISMATCH"))
            .thenReturn(new PreviewService.PreviewRepairResult(
                documentId,
                "FAILED",
                true,
                "STALE_HASH_MISMATCH",
                "hash-b",
                "hash-a"
            ));
        PreviewQueueService.PreviewQueueStatus queueStatus = new PreviewQueueService.PreviewQueueStatus(
            documentId,
            PreviewStatus.FAILED,
            true,
            0,
            null,
            "Preview queued"
        );
        Mockito.when(previewQueueService.enqueue(documentId, true)).thenReturn(queueStatus);
        Mockito.when(renditionResourceService.resolvePreviewMutationStatus(document, queueStatus)).thenReturn(
            new RenditionResourceService.PreviewMutationStatus(
                documentId,
                "PROCESSING",
                null,
                null,
                LocalDateTime.of(2026, 3, 29, 9, 15),
                true,
                0,
                null,
                "Preview queued"
            )
        );

        mockMvc.perform(post("/api/v1/documents/{documentId}/preview/repair", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previewStatus").value("PROCESSING"))
            .andExpect(jsonPath("$.previewLastUpdated").exists());
    }

    @Test
    @DisplayName("Cancel preview queue endpoint returns cancellation state")
    void cancelQueuedPreviewShouldReturnQueueCancellationStatus() throws Exception {
        UUID documentId = UUID.randomUUID();
        Mockito.when(previewQueueService.cancel(documentId))
            .thenReturn(new PreviewQueueService.PreviewQueueCancellationStatus(
                documentId,
                "CANCELLED",
                true,
                true,
                false,
                "Cancelled queued preview task"
            ));

        mockMvc.perform(post("/api/v1/documents/{documentId}/preview/queue/cancel", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.queueState").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelled").value(true))
            .andExpect(jsonPath("$.hadActiveTask").value(true))
            .andExpect(jsonPath("$.running").value(false))
            .andExpect(jsonPath("$.message").value("Cancelled queued preview task"));
    }
}
