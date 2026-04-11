package com.ecm.core.controller;

import com.ecm.core.conversion.ConversionService;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.ocr.OcrQueueService;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.preview.PreviewService;
import com.ecm.core.service.CheckOutCheckInService;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.TenantQuotaService;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerWorkingCopyTest {

    private MockMvc mockMvc;

    @Mock private NodeService nodeService;
    @Mock private VersionService versionService;
    @Mock private ContentService contentService;
    @Mock private TenantQuotaService tenantQuotaService;
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
            tenantQuotaService,
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

    // ------------------------------------------------------------------ checkout-wc

    @Test
    @DisplayName("POST checkout-wc returns working copy with isWorkingCopy=true")
    void checkoutWcReturnsWorkingCopy() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID wcId = UUID.randomUUID();

        Document wc = workingCopy(wcId, docId, "report.docx", "alice");

        Mockito.when(checkOutCheckInService.checkout(docId, null)).thenReturn(wc);

        mockMvc.perform(post("/api/v1/documents/{documentId}/checkout-wc", docId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(wcId.toString()))
            .andExpect(jsonPath("$.isWorkingCopy").value(true))
            .andExpect(jsonPath("$.workingCopyOf").value(docId.toString()))
            .andExpect(jsonPath("$.checkoutUser").value("alice"))
            .andExpect(jsonPath("$.checkedOut").value(true));
    }

    @Test
    @DisplayName("POST checkout-wc with destination passes folder ID")
    void checkoutWcWithDestination() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        UUID wcId = UUID.randomUUID();

        Document wc = workingCopy(wcId, docId, "report.docx", "alice");

        Mockito.when(checkOutCheckInService.checkout(docId, destId)).thenReturn(wc);

        mockMvc.perform(post("/api/v1/documents/{documentId}/checkout-wc", docId)
                .param("destination", destId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isWorkingCopy").value(true));
    }

    // ------------------------------------------------------------------ checkin-wc

    @Test
    @DisplayName("POST checkin-wc returns original document with cleared checkout")
    void checkinWcReturnsOriginal() throws Exception {
        UUID wcId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();

        Document wcDoc = workingCopy(wcId, originalId, "report.docx", "alice");
        Document original = document(originalId, "report.docx");
        original.checkin();

        Mockito.when(nodeService.getNode(wcId)).thenReturn(wcDoc);
        Mockito.when(checkOutCheckInService.checkin(wcId, false)).thenReturn(original);

        mockMvc.perform(post("/api/v1/documents/{workingCopyId}/checkin-wc", wcId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(originalId.toString()))
            .andExpect(jsonPath("$.checkedOut").value(false))
            .andExpect(jsonPath("$.isWorkingCopy").value(false));
    }

    @Test
    @DisplayName("POST checkin-wc rejects non-working-copy")
    void checkinWcRejectsNonWc() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = document(docId, "report.docx");

        Mockito.when(nodeService.getNode(docId)).thenReturn(doc);

        mockMvc.perform(post("/api/v1/documents/{workingCopyId}/checkin-wc", docId))
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ cancel-checkout-wc

    @Test
    @DisplayName("POST cancel-checkout-wc returns original with cleared state")
    void cancelCheckoutWcReturnsOriginal() throws Exception {
        UUID docId = UUID.randomUUID();
        Document original = document(docId, "report.docx");
        original.checkin();

        Mockito.when(checkOutCheckInService.cancelCheckout(docId)).thenReturn(original);

        mockMvc.perform(post("/api/v1/documents/{documentId}/cancel-checkout-wc", docId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedOut").value(false));
    }

    // ------------------------------------------------------------------ working-copy

    @Test
    @DisplayName("GET working-copy returns working copy when exists")
    void getWorkingCopyReturnsWc() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID wcId = UUID.randomUUID();
        Document wc = workingCopy(wcId, docId, "report.docx", "alice");

        Mockito.when(checkOutCheckInService.getWorkingCopy(docId)).thenReturn(Optional.of(wc));

        mockMvc.perform(get("/api/v1/documents/{documentId}/working-copy", docId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(wcId.toString()))
            .andExpect(jsonPath("$.isWorkingCopy").value(true));
    }

    @Test
    @DisplayName("GET working-copy returns 404 when none exists")
    void getWorkingCopyReturns404WhenNone() throws Exception {
        UUID docId = UUID.randomUUID();

        Mockito.when(checkOutCheckInService.getWorkingCopy(docId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/documents/{documentId}/working-copy", docId))
            .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ original

    @Test
    @DisplayName("GET original returns original document from working copy ID")
    void getOriginalReturnsOriginal() throws Exception {
        UUID wcId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        Document original = document(originalId, "report.docx");

        Mockito.when(checkOutCheckInService.getOriginal(wcId)).thenReturn(Optional.of(original));

        mockMvc.perform(get("/api/v1/documents/{workingCopyId}/original", wcId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(originalId.toString()))
            .andExpect(jsonPath("$.isWorkingCopy").value(false));
    }

    @Test
    @DisplayName("GET original returns 404 for non-working-copy")
    void getOriginalReturns404ForNonWc() throws Exception {
        UUID docId = UUID.randomUUID();

        Mockito.when(checkOutCheckInService.getOriginal(docId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/documents/{workingCopyId}/original", docId))
            .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    private Document document(UUID id, String name) {
        Document doc = new Document();
        doc.setId(id);
        doc.setName(name);
        doc.setMimeType("application/pdf");
        doc.setPath("/" + name);
        return doc;
    }

    private Document workingCopy(UUID wcId, UUID originalId, String originalName, String user) {
        Document wc = new Document();
        wc.setId(wcId);
        wc.setName("(Working Copy) " + originalName);
        wc.setMimeType("application/pdf");
        wc.setPath("/(Working Copy) " + originalName);
        wc.setWorkingCopy(true);
        wc.setWorkingCopyOf(originalId);
        wc.setCheckoutUser(user);
        wc.setCheckoutDate(java.time.LocalDateTime.now());
        return wc;
    }
}
