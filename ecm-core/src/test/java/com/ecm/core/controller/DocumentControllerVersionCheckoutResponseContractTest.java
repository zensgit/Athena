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
import com.ecm.core.service.NodePropertyEncryptionService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.PdfAnnotationService;
import com.ecm.core.service.RenditionResourceService;
import com.ecm.core.service.TenantQuotaService;
import com.ecm.core.service.VersionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerVersionCheckoutResponseContractTest {

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
    @Mock private NodePropertyEncryptionService nodePropertyEncryptionService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

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
            checkOutCheckInService,
            nodePropertyEncryptionService
        );
        ReflectionTestUtils.setField(controller, "previewReadHashEnforceEnabled", true);
        ReflectionTestUtils.setField(controller, "previewReadAutoRepairOnStale", true);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    @DisplayName("GET /documents/{id}/versions locks VersionDto field set and nullable version fields")
    void getVersionHistoryLocksVersionDtoContract() throws Exception {
        UUID documentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID baselineVersionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID currentVersionId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        Document document = new Document();
        document.setId(documentId);
        document.setCheckoutBaselineVersionId(baselineVersionId.toString());

        Version baseline = version(document, baselineVersionId, "1.0", null, null, null, null);
        Version current = version(
            document,
            currentVersionId,
            "1.1",
            "Minor update",
            LocalDateTime.of(2026, 5, 23, 10, 15, 0),
            "alice",
            Version.VersionStatus.RELEASED
        );
        current.setContentHash("sha256:abc");
        current.setContentId("content-1");
        document.setCurrentVersion(current);

        Mockito.when(versionService.getVersionHistory(documentId, true)).thenReturn(List.of(baseline, current));

        MvcResult result = mockMvc.perform(get("/api/v1/documents/{documentId}/versions", documentId)
                .param("majorOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(baselineVersionId.toString()))
            .andExpect(jsonPath("$[0].comment", nullValue()))
            .andExpect(jsonPath("$[0].createdDate", nullValue()))
            .andExpect(jsonPath("$[0].creator", nullValue()))
            .andExpect(jsonPath("$[0].contentHash", nullValue()))
            .andExpect(jsonPath("$[0].contentId", nullValue()))
            .andExpect(jsonPath("$[0].status", nullValue()))
            .andExpect(jsonPath("$[0].checkoutBaseline").value(true))
            .andExpect(jsonPath("$[0].checkoutCurrent").value(false))
            .andExpect(jsonPath("$[1].createdDate").value("2026-05-23T10:15:00"))
            .andExpect(jsonPath("$[1].checkoutCurrent").value(true))
            .andReturn();

        JsonNode first = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(versionDtoFieldNames(), fieldNames(first));
    }

    @Test
    @DisplayName("GET /documents/{id}/checkout-info locks CheckoutInfoDto field set and nullable fields")
    void getCheckoutInfoLocksCheckoutInfoContract() throws Exception {
        UUID documentId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Mockito.when(nodeService.getCheckoutInfo(documentId)).thenReturn(new CheckoutInfoDto(
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
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/documents/{documentId}/checkout-info", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHECKED_OUT_BY_OTHER"))
            .andExpect(jsonPath("$.checkoutUser").value("bob"))
            .andExpect(jsonPath("$.checkoutDate", nullValue()))
            .andExpect(jsonPath("$.checkoutAgeSeconds").value(300))
            .andExpect(jsonPath("$.canCheckout").value(false))
            .andExpect(jsonPath("$.canCheckIn").value(false))
            .andExpect(jsonPath("$.canCancelCheckout").value(false))
            .andExpect(jsonPath("$.canKeepCheckedOut").value(false))
            .andExpect(jsonPath("$.requiresNewVersionFile").value(true))
            .andExpect(jsonPath("$.blockingReason").value("Checked out by bob."))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(checkoutInfoDtoFieldNames(), fieldNames(root));
    }

    private static Version version(
        Document document,
        UUID id,
        String label,
        String comment,
        LocalDateTime createdDate,
        String createdBy,
        Version.VersionStatus status
    ) {
        Version version = new Version();
        version.setId(id);
        version.setDocument(document);
        version.setVersionLabel(label);
        version.setMajorVersion(1);
        version.setMinorVersion(0);
        version.setFileSize(128L);
        version.setMimeType("application/pdf");
        version.setComment(comment);
        version.setCreatedDate(createdDate);
        version.setCreatedBy(createdBy);
        version.setStatus(status);
        version.setMajorVersionFlag(true);
        return version;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> versionDtoFieldNames() {
        return List.of(
            "id",
            "documentId",
            "versionLabel",
            "comment",
            "createdDate",
            "creator",
            "size",
            "major",
            "mimeType",
            "contentHash",
            "contentId",
            "status",
            "checkoutBaseline",
            "checkoutCurrent"
        );
    }

    private static List<String> checkoutInfoDtoFieldNames() {
        return List.of(
            "status",
            "checkoutUser",
            "checkoutDate",
            "checkoutAgeSeconds",
            "canCheckout",
            "canCheckIn",
            "canCancelCheckout",
            "canKeepCheckedOut",
            "requiresNewVersionFile",
            "blockingReason"
        );
    }
}
