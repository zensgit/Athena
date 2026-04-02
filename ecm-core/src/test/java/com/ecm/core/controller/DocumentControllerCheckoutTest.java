package com.ecm.core.controller;

import com.ecm.core.conversion.ConversionService;
import com.ecm.core.dto.CheckoutInfoDto;
import com.ecm.core.entity.CheckoutStatus;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Version;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerCheckoutTest {

    private MockMvc mockMvc;

    @Mock private NodeService nodeService;
    @Mock private VersionService versionService;
    @Mock private ContentService contentService;
    @Mock private PreviewService previewService;
    @Mock private PreviewQueueService previewQueueService;
    @Mock private OcrQueueService ocrQueueService;
    @Mock private ConversionService conversionService;
    @Mock private PdfAnnotationService pdfAnnotationService;
    @Mock private RenditionResourceService renditionResourceService;
    @Mock private CheckOutCheckInService checkOutCheckInService;

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
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("Checkout returns persisted checkout metadata")
    void checkoutReturnsMetadata() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkout("alice");

        Mockito.when(nodeService.checkoutDocument(documentId)).thenReturn(document);

        mockMvc.perform(post("/api/v1/documents/{documentId}/checkout", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedOut").value(true))
            .andExpect(jsonPath("$.checkoutUser").value("alice"));
    }

    @Test
    @DisplayName("Checkout response prefers rendition summary preview semantics")
    void checkoutReturnsRenditionSummaryPreviewSemantics() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "blob.bin");
        document.setMimeType("application/octet-stream");
        document.checkout("alice");

        Mockito.when(nodeService.checkoutDocument(documentId)).thenReturn(document);
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                null,
                null
            )
        );

        mockMvc.perform(post("/api/v1/documents/{documentId}/checkout", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedOut").value(true))
            .andExpect(jsonPath("$.previewStatus").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.previewFailureCategory").value("UNSUPPORTED"));
    }

    @Test
    @DisplayName("Checkout info returns caller-relative checkout affordances")
    void checkoutInfoReturnsAffordances() throws Exception {
        UUID documentId = UUID.randomUUID();

        Mockito.when(nodeService.getCheckoutInfo(documentId)).thenReturn(
            new CheckoutInfoDto(
                CheckoutStatus.CHECKED_OUT_BY_OTHER,
                "bob",
                null,
                300L,
                false,
                false,
                false,
                false,
                true,
                "Checked out by bob."
            )
        );

        mockMvc.perform(get("/api/v1/documents/{documentId}/checkout-info", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHECKED_OUT_BY_OTHER"))
            .andExpect(jsonPath("$.checkoutUser").value("bob"))
            .andExpect(jsonPath("$.canCheckIn").value(false))
            .andExpect(jsonPath("$.blockingReason").value("Checked out by bob."));
    }

    @Test
    @DisplayName("Checkout lineage returns checkout info plus baseline and current versions")
    void checkoutLineageReturnsVersions() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID baselineVersionId = UUID.randomUUID();
        UUID currentVersionId = UUID.randomUUID();

        Document document = document(documentId, "contract.pdf");
        document.checkout("alice");
        document.setCheckoutBaselineVersionId(baselineVersionId.toString());

        Version currentVersion = new Version();
        currentVersion.setId(currentVersionId);
        currentVersion.setDocument(document);
        currentVersion.setVersionLabel("1.4");
        currentVersion.setFileSize(200L);
        currentVersion.setMimeType("application/pdf");
        document.setCurrentVersion(currentVersion);

        Version baselineVersion = new Version();
        baselineVersion.setId(baselineVersionId);
        baselineVersion.setDocument(document);
        baselineVersion.setVersionLabel("1.3");
        baselineVersion.setFileSize(180L);
        baselineVersion.setMimeType("application/pdf");

        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(nodeService.getCheckoutInfo(documentId)).thenReturn(
            new CheckoutInfoDto(
                CheckoutStatus.CHECKED_OUT_BY_YOU,
                "alice",
                document.getCheckoutDate(),
                300L,
                false,
                true,
                true,
                true,
                true,
                null
            )
        );
        Mockito.when(versionService.getVersion(baselineVersionId)).thenReturn(baselineVersion);

        mockMvc.perform(get("/api/v1/documents/{documentId}/checkout-lineage", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.checkout.status").value("CHECKED_OUT_BY_YOU"))
            .andExpect(jsonPath("$.baselineVersion.id").value(baselineVersionId.toString()))
            .andExpect(jsonPath("$.baselineVersion.versionLabel").value("1.3"))
            .andExpect(jsonPath("$.currentVersion.id").value(currentVersionId.toString()))
            .andExpect(jsonPath("$.currentVersion.versionLabel").value("1.4"));
    }

    @Test
    @DisplayName("Checkin uses service path and clears checkout metadata")
    void checkinClearsMetadata() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkin();

        Mockito.when(nodeService.checkinDocument(documentId, false)).thenReturn(document);

        mockMvc.perform(post("/api/v1/documents/{documentId}/checkin", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedOut").value(false));
    }

    @Test
    @DisplayName("Checkin supports keepCheckedOut semantics")
    void checkinKeepsCheckoutMetadata() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkout("alice");
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", "next".getBytes());

        Mockito.when(nodeService.checkinDocument(documentId, true)).thenReturn(document);

        mockMvc.perform(multipart("/api/v1/documents/{documentId}/checkin", documentId)
                .file(file)
                .param("keepCheckedOut", "true")
                .param("majorVersion", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedOut").value(true))
            .andExpect(jsonPath("$.checkoutUser").value("alice"));
    }

    @Test
    @DisplayName("Checkin rejects keepCheckedOut without file")
    void checkinRejectsKeepCheckedOutWithoutFile() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/documents/{documentId}/checkin", documentId)
                .param("keepCheckedOut", "true"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Cancel checkout uses service path and clears checkout metadata")
    void cancelCheckoutClearsMetadata() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkin();

        Mockito.when(nodeService.cancelCheckoutDocument(documentId)).thenReturn(document);

        mockMvc.perform(post("/api/v1/documents/{documentId}/cancel-checkout", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedOut").value(false));
    }

    private Document document(UUID id, String name) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setMimeType("application/pdf");
        document.setPath("/" + name);
        return document;
    }
}
