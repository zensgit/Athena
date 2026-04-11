package com.ecm.core.cmis;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.RenditionResource;
import com.ecm.core.entity.RenditionState;
import com.ecm.core.service.RenditionResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmisRenditionServiceTest {

    @Mock
    private RenditionResourceService renditionResourceService;

    private CmisRenditionService cmisRenditionService;

    private final UUID nodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cmisRenditionService = new CmisRenditionService(renditionResourceService);
    }

    @Test
    @DisplayName("getRenditions returns available renditions mapped to entries")
    void getRenditionsReturnsAvailableRenditions() {
        RenditionResource preview = buildRendition("preview", "Preview", "application/pdf", true);
        RenditionResource thumbnail = buildRendition("thumbnail", "Thumbnail", "image/png", true);
        when(renditionResourceService.listForNode(nodeId)).thenReturn(List.of(preview, thumbnail));

        CmisModels.RenditionsResponse response = cmisRenditionService.getRenditions(nodeId.toString(), null);

        assertEquals(nodeId.toString(), response.objectId());
        assertEquals(2, response.renditions().size());
        assertEquals("preview", response.renditions().get(0).kind());
        assertEquals("application/pdf", response.renditions().get(0).mimeType());
        assertEquals("Preview", response.renditions().get(0).title());
        assertEquals("thumbnail", response.renditions().get(1).kind());
        assertEquals("image/png", response.renditions().get(1).mimeType());
    }

    @Test
    @DisplayName("filter cmis:none returns empty list")
    void filterCmisNoneReturnsEmpty() {
        CmisModels.RenditionsResponse response = cmisRenditionService.getRenditions(nodeId.toString(), "cmis:none");

        assertEquals(nodeId.toString(), response.objectId());
        assertTrue(response.renditions().isEmpty());
    }

    @Test
    @DisplayName("filter by mime type returns matching renditions")
    void filterByMimeType() {
        RenditionResource preview = buildRendition("preview", "Preview", "application/pdf", true);
        RenditionResource thumbnail = buildRendition("thumbnail", "Thumbnail", "image/png", true);
        when(renditionResourceService.listForNode(nodeId)).thenReturn(List.of(preview, thumbnail));

        CmisModels.RenditionsResponse response = cmisRenditionService.getRenditions(nodeId.toString(), "image/png");

        assertEquals(1, response.renditions().size());
        assertEquals("thumbnail", response.renditions().get(0).kind());
    }

    @Test
    @DisplayName("filter by wildcard mime type works")
    void filterByWildcardMimeType() {
        RenditionResource preview = buildRendition("preview", "Preview", "application/pdf", true);
        RenditionResource thumbnail = buildRendition("thumbnail", "Thumbnail", "image/png", true);
        when(renditionResourceService.listForNode(nodeId)).thenReturn(List.of(preview, thumbnail));

        CmisModels.RenditionsResponse response = cmisRenditionService.getRenditions(nodeId.toString(), "image/*");

        assertEquals(1, response.renditions().size());
        assertEquals("thumbnail", response.renditions().get(0).kind());
        assertEquals("image/png", response.renditions().get(0).mimeType());
    }

    @Test
    @DisplayName("unavailable renditions are excluded")
    void unavailableRenditionsExcluded() {
        RenditionResource available = buildRendition("preview", "Preview", "application/pdf", true);
        RenditionResource unavailable = buildRendition("thumbnail", "Thumbnail", "image/png", false);
        when(renditionResourceService.listForNode(nodeId)).thenReturn(List.of(available, unavailable));

        CmisModels.RenditionsResponse response = cmisRenditionService.getRenditions(nodeId.toString(), "*");

        assertEquals(1, response.renditions().size());
        assertEquals("preview", response.renditions().get(0).kind());
    }

    @Test
    @DisplayName("folder node returns empty renditions")
    void folderNodeReturnsEmptyRenditions() {
        when(renditionResourceService.listForNode(nodeId)).thenReturn(List.of());

        CmisModels.RenditionsResponse response = cmisRenditionService.getRenditions(nodeId.toString(), null);

        assertEquals(nodeId.toString(), response.objectId());
        assertTrue(response.renditions().isEmpty());
    }

    @Test
    @DisplayName("filter by rendition key returns matching renditions")
    void filterByRenditionKey() {
        RenditionResource preview = buildRendition("preview", "Preview", "application/pdf", true);
        RenditionResource thumbnail = buildRendition("thumbnail", "Thumbnail", "image/png", true);
        when(renditionResourceService.listForNode(nodeId)).thenReturn(List.of(preview, thumbnail));

        CmisModels.RenditionsResponse response = cmisRenditionService.getRenditions(nodeId.toString(), "preview");

        assertEquals(1, response.renditions().size());
        assertEquals("preview", response.renditions().get(0).kind());
    }

    private RenditionResource buildRendition(String key, String label, String mimeType, boolean available) {
        Document document = new Document();
        document.setId(nodeId);

        return RenditionResource.builder()
            .id(UUID.randomUUID())
            .document(document)
            .renditionKey(key)
            .label(label)
            .mimeType(mimeType)
            .state(available ? RenditionState.READY : RenditionState.REGISTERED)
            .available(available)
            .downloadable(true)
            .applicable(true)
            .sortOrder(0)
            .contentUrl(available ? "/content/" + key : null)
            .build();
    }
}
