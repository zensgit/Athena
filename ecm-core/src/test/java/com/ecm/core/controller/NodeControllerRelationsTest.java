package com.ecm.core.controller;

import com.ecm.core.dto.CheckoutInfoDto;
import com.ecm.core.entity.CheckoutStatus;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.entity.RenditionResource;
import com.ecm.core.entity.RenditionState;
import com.ecm.core.entity.Version;
import com.ecm.core.service.DocumentRelationService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.RenditionResourceService;
import com.ecm.core.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NodeControllerRelationsTest {

    private MockMvc mockMvc;

    @Mock
    private NodeService nodeService;

    @Mock
    private DocumentRelationService relationService;

    @Mock
    private VersionService versionService;

    @Mock
    private RenditionResourceService renditionResourceService;

    @InjectMocks
    private NodeController nodeController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(nodeController).build();
    }

    @Test
    @DisplayName("Node relations summary includes relation and version counts for documents")
    void getNodeRelationsSummaryShouldReturnDocumentStats() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Folder parent = new Folder();
        parent.setId(parentId);
        parent.setName("parent");
        parent.setPath("/parent");

        Document document = new Document();
        document.setId(nodeId);
        document.setName("contract.pdf");
        document.setPath("/parent/contract.pdf");
        document.setParent(parent);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(true);
        document.setCheckoutUser("alice");
        document.setCheckoutDate(java.time.LocalDateTime.of(2026, 3, 27, 9, 30));
        document.setCheckoutBaselineVersionId("baseline-version-id");
        document.setCheckoutBaselineVersionLabel("1.3");

        Page<DocumentRelation> outgoing = new PageImpl<>(List.of(), PageRequest.of(0, 1), 3);
        Page<DocumentRelation> incoming = new PageImpl<>(List.of(), PageRequest.of(0, 1), 2);
        Page<Version> versions = new PageImpl<>(List.of(), PageRequest.of(0, 1), 4);

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(nodeService.getNode(parentId)).thenReturn(parent);
        when(nodeService.getChildren(eq(nodeId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 5));
        when(relationService.getOutgoingRelationsPage(eq(nodeId), any(Pageable.class), eq((String) null)))
            .thenReturn(outgoing);
        when(relationService.getIncomingRelationsPage(eq(nodeId), any(Pageable.class), eq((String) null)))
            .thenReturn(incoming);
        when(versionService.getVersionHistory(eq(nodeId), any(Pageable.class), eq(false)))
            .thenReturn(versions);
        when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                nodeId,
                true,
                "READY",
                true,
                null,
                null,
                java.time.LocalDateTime.of(2026, 3, 27, 9, 35),
                "1.2"
            )
        );

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/summary", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.nodeType").value("DOCUMENT"))
            .andExpect(jsonPath("$.parentCount").value(1))
            .andExpect(jsonPath("$.childCount").value(5))
            .andExpect(jsonPath("$.sourceRelationCount").value(3))
            .andExpect(jsonPath("$.targetRelationCount").value(2))
            .andExpect(jsonPath("$.versionCount").value(4))
            .andExpect(jsonPath("$.previewStatus").value("READY"))
            .andExpect(jsonPath("$.renditionAvailable").value(true))
            .andExpect(jsonPath("$.checkedOut").value(true))
            .andExpect(jsonPath("$.checkoutUser").value("alice"))
            .andExpect(jsonPath("$.checkoutDate").exists());
    }

    @Test
    @DisplayName("Node checkout relation returns caller-relative checkout metadata for documents")
    void getNodeRelationCheckoutShouldReturnCheckoutMetadata() throws Exception {
        UUID nodeId = UUID.randomUUID();

        Document document = new Document();
        document.setId(nodeId);
        document.setName("contract.docx");
        document.setPath("/workspace/contract.docx");
        document.setCheckoutUser("alice");
        document.setCheckoutDate(java.time.LocalDateTime.of(2026, 3, 27, 9, 30));
        document.setCheckoutBaselineVersionId("baseline-version-id");
        document.setCheckoutBaselineVersionLabel("1.3");
        document.setVersionLabel("1.4");

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(nodeService.getCheckoutInfo(nodeId)).thenReturn(new CheckoutInfoDto(
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
        ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/checkout", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.document").value(true))
            .andExpect(jsonPath("$.checkedOut").value(true))
            .andExpect(jsonPath("$.checkoutUser").value("alice"))
            .andExpect(jsonPath("$.checkoutBaselineVersionId").value("baseline-version-id"))
            .andExpect(jsonPath("$.checkoutBaselineVersionLabel").value("1.3"))
            .andExpect(jsonPath("$.currentVersionLabel").value("1.4"))
            .andExpect(jsonPath("$.canCheckIn").value(true))
            .andExpect(jsonPath("$.canCancelCheckout").value(true))
            .andExpect(jsonPath("$.canKeepCheckedOut").value(true))
            .andExpect(jsonPath("$.requiresNewVersionFile").value(true));
    }

    @Test
    @DisplayName("Node checkout graph exposes virtual working-copy lineage for active checkout")
    void getNodeRelationCheckoutGraphShouldReturnVirtualLineage() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID baselineVersionId = UUID.randomUUID();
        UUID currentVersionId = UUID.randomUUID();

        Document document = new Document();
        document.setId(nodeId);
        document.setName("contract.docx");
        document.setPath("/workspace/contract.docx");
        Folder parent = new Folder();
        parent.setId(UUID.randomUUID());
        parent.setName("workspace");
        parent.setPath("/workspace");
        document.setParent(parent);
        document.setCheckoutUser("alice");
        document.setCheckoutDate(java.time.LocalDateTime.of(2026, 3, 27, 9, 30));
        document.setCheckoutBaselineVersionId(baselineVersionId.toString());
        document.setCheckoutBaselineVersionLabel("1.3");
        document.setVersionLabel("1.4");

        Version currentVersion = new Version();
        currentVersion.setId(currentVersionId);
        currentVersion.setDocument(document);
        currentVersion.setVersionLabel("1.4");
        currentVersion.setFileSize(200L);
        currentVersion.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        document.setCurrentVersion(currentVersion);

        Version baselineVersion = new Version();
        baselineVersion.setId(baselineVersionId);
        baselineVersion.setDocument(document);
        baselineVersion.setVersionLabel("1.3");
        baselineVersion.setFileSize(180L);
        baselineVersion.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(nodeService.getCheckoutInfo(nodeId)).thenReturn(new CheckoutInfoDto(
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
        ));
        when(versionService.getVersion(baselineVersionId)).thenReturn(baselineVersion);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/checkout-graph", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.document").value(true))
            .andExpect(jsonPath("$.checkedOut").value(true))
            .andExpect(jsonPath("$.documentNode.id").value(nodeId.toString()))
            .andExpect(jsonPath("$.workingCopyNode.id").value("working-copy:" + nodeId))
            .andExpect(jsonPath("$.workingCopyNode.virtualNode").value(true))
            .andExpect(jsonPath("$.destinationNode.id").value(parent.getId().toString()))
            .andExpect(jsonPath("$.destinationNode.label").value("/workspace"))
            .andExpect(jsonPath("$.baselineVersion.id").value(baselineVersionId.toString()))
            .andExpect(jsonPath("$.baselineVersion.versionLabel").value("1.3"))
            .andExpect(jsonPath("$.currentVersion.id").value(currentVersionId.toString()))
            .andExpect(jsonPath("$.currentVersion.versionLabel").value("1.4"))
            .andExpect(jsonPath("$.nodes.length()").value(5))
            .andExpect(jsonPath("$.nodes[0].kind").value("DOCUMENT"))
            .andExpect(jsonPath("$.nodes[1].kind").value("WORKING_COPY"))
            .andExpect(jsonPath("$.nodes[2].kind").value("BASELINE_VERSION"))
            .andExpect(jsonPath("$.nodes[3].kind").value("CURRENT_VERSION"))
            .andExpect(jsonPath("$.nodes[4].kind").value("DESTINATION_FOLDER"))
            .andExpect(jsonPath("$.edges.length()").value(6))
            .andExpect(jsonPath("$.edges[0].relationType").value("HAS_WORKING_COPY"))
            .andExpect(jsonPath("$.edges[1].relationType").value("WORKING_COPY_BASELINE"))
            .andExpect(jsonPath("$.edges[2].relationType").value("WORKING_COPY_CURRENT"))
            .andExpect(jsonPath("$.edges[3].relationType").value("CHECKIN_DESTINATION"))
            .andExpect(jsonPath("$.edges[4].relationType").value("DOCUMENT_CURRENT_VERSION"))
            .andExpect(jsonPath("$.edges[5].relationType").value("CHECKOUT_BASELINE_VERSION"));
    }

    @Test
    @DisplayName("Node checkout graph exposes current-version edge for available documents")
    void getNodeRelationCheckoutGraphShouldExposeCurrentVersionForAvailableDocument() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID currentVersionId = UUID.randomUUID();

        Document document = new Document();
        document.setId(nodeId);
        document.setName("contract.docx");
        document.setPath("/workspace/contract.docx");
        document.setVersionLabel("2.1");

        Version currentVersion = new Version();
        currentVersion.setId(currentVersionId);
        currentVersion.setDocument(document);
        currentVersion.setVersionLabel("2.1");
        currentVersion.setFileSize(220L);
        currentVersion.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        document.setCurrentVersion(currentVersion);

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(nodeService.getCheckoutInfo(nodeId)).thenReturn(new CheckoutInfoDto(
            CheckoutStatus.AVAILABLE,
            null,
            null,
            null,
            true,
            false,
            false,
            false,
            false,
            null
        ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/checkout-graph", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedOut").value(false))
            .andExpect(jsonPath("$.nodes.length()").value(2))
            .andExpect(jsonPath("$.nodes[0].kind").value("DOCUMENT"))
            .andExpect(jsonPath("$.nodes[1].kind").value("CURRENT_VERSION"))
            .andExpect(jsonPath("$.edges.length()").value(1))
            .andExpect(jsonPath("$.edges[0].relationType").value("DOCUMENT_CURRENT_VERSION"))
            .andExpect(jsonPath("$.edges[0].targetId").value(currentVersionId.toString()));
    }

    @Test
    @DisplayName("Node relation sources returns empty page for folders")
    void getNodeRelationSourcesShouldReturnEmptyForFolder() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(nodeId);
        folder.setName("workspace");
        folder.setPath("/workspace");

        when(nodeService.getNode(nodeId)).thenReturn(folder);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/sources", nodeId)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0));

        verify(relationService, never()).getIncomingRelationsPage(any(UUID.class), any(Pageable.class), any());
    }

    @Test
    @DisplayName("Node relation parents returns ancestor chain from root to direct parent")
    void getNodeRelationParentsShouldReturnRootToDirectParentOrder() throws Exception {
        UUID grandparentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        Folder grandparent = new Folder();
        grandparent.setId(grandparentId);
        grandparent.setName("root");
        grandparent.setPath("/root");

        Folder parent = new Folder();
        parent.setId(parentId);
        parent.setName("workspace");
        parent.setPath("/root/workspace");
        parent.setParent(grandparent);

        Document child = new Document();
        child.setId(childId);
        child.setName("design.pdf");
        child.setPath("/root/workspace/design.pdf");
        child.setParent(parent);

        when(nodeService.getNode(childId)).thenReturn(child);
        when(nodeService.getNode(parentId)).thenReturn(parent);
        when(nodeService.getNode(grandparentId)).thenReturn(grandparent);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/parents", childId)
                .param("maxDepth", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(grandparentId.toString()))
            .andExpect(jsonPath("$[0].name").value("root"))
            .andExpect(jsonPath("$[1].id").value(parentId.toString()))
            .andExpect(jsonPath("$[1].name").value("workspace"));
    }

    @Test
    @DisplayName("Node relation targets returns paged outgoing relations for document node")
    void getNodeRelationTargetsShouldReturnOutgoingRelationsPage() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();

        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName("workspace");
        folder.setPath("/workspace");

        Document source = new Document();
        source.setId(nodeId);
        source.setName("source-doc.pdf");
        source.setPath("/workspace/source-doc.pdf");
        source.setParent(folder);

        Document target = new Document();
        target.setId(targetId);
        target.setName("target-attachment.pdf");
        target.setPath("/workspace/target-attachment.pdf");
        target.setParent(folder);

        DocumentRelation relation = new DocumentRelation();
        relation.setId(relationId);
        relation.setRelationType("ATTACHMENT");
        relation.setSource(source);
        relation.setTarget(target);

        when(nodeService.getNode(nodeId)).thenReturn(source);
        when(relationService.getOutgoingRelationsPage(eq(nodeId), any(Pageable.class), eq((String) null)))
            .thenReturn(new PageImpl<>(List.of(relation), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/targets", nodeId)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].relationType").value("ATTACHMENT"))
            .andExpect(jsonPath("$.content[0].source.name").value("source-doc.pdf"))
            .andExpect(jsonPath("$.content[0].target.name").value("target-attachment.pdf"));
    }

    @Test
    @DisplayName("Node relation versions marks checkout baseline and current versions")
    void getNodeRelationVersionsShouldExposeCheckoutVersionMarkers() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID baselineVersionId = UUID.randomUUID();
        UUID currentVersionId = UUID.randomUUID();

        Document document = new Document();
        document.setId(nodeId);
        document.setName("design.pdf");
        document.setPath("/workspace/design.pdf");
        document.setCheckoutBaselineVersionId(baselineVersionId.toString());

        Version baseline = new Version();
        baseline.setId(baselineVersionId);
        baseline.setDocument(document);
        baseline.setVersionLabel("1.3");
        baseline.setFileSize(120L);

        Version current = new Version();
        current.setId(currentVersionId);
        current.setDocument(document);
        current.setVersionLabel("1.4");
        current.setFileSize(180L);
        document.setCurrentVersion(current);

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(versionService.getVersionHistory(eq(nodeId), any(Pageable.class), eq(false)))
            .thenReturn(new PageImpl<>(List.of(current, baseline), PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/versions", nodeId)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].versionLabel").value("1.4"))
            .andExpect(jsonPath("$.content[0].checkoutCurrent").value(true))
            .andExpect(jsonPath("$.content[0].checkoutBaseline").value(false))
            .andExpect(jsonPath("$.content[1].versionLabel").value("1.3"))
            .andExpect(jsonPath("$.content[1].checkoutBaseline").value(true))
            .andExpect(jsonPath("$.content[1].checkoutCurrent").value(false));
    }

    @Test
    @DisplayName("Node relation versions returns empty page for folders and skips version service")
    void getNodeRelationVersionsShouldReturnEmptyForFolder() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(nodeId);
        folder.setName("workspace");
        folder.setPath("/workspace");

        when(nodeService.getNode(nodeId)).thenReturn(folder);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/versions", nodeId)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0));

        verify(versionService, never()).getVersionHistory(any(UUID.class), any(Pageable.class), anyBoolean());
    }

    @Test
    @DisplayName("Node relation renditions returns preview and thumbnail virtual resources for document")
    void getNodeRelationRenditionsShouldReturnVirtualRenditionsForDocument() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Folder parent = new Folder();
        parent.setId(UUID.randomUUID());
        parent.setName("workspace");
        parent.setPath("/workspace");

        Document document = new Document();
        document.setId(nodeId);
        document.setName("report.pdf");
        document.setPath("/workspace/report.pdf");
        document.setParent(parent);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.READY);
        document.setPreviewAvailable(true);
        document.setThumbnailId("thumb-1");
        document.setPreviewLastUpdated(java.time.LocalDateTime.of(2026, 3, 18, 10, 15));
        document.setVersionLabel("1.2");

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(renditionResourceService.listForDocument(document)).thenReturn(List.of(
            RenditionResource.builder()
                .document(document)
                .renditionKey("preview")
                .label("Preview")
                .mimeType("application/json")
                .state(RenditionState.READY)
                .available(true)
                .downloadable(false)
                .contentUrl("/api/v1/documents/" + nodeId + "/preview")
                .sourceStatus("READY")
                .sourceUpdatedAt(java.time.LocalDateTime.of(2026, 3, 18, 10, 15))
                .versionLabel("1.2")
                .sortOrder(0)
                .build(),
            RenditionResource.builder()
                .document(document)
                .renditionKey("thumbnail")
                .label("Thumbnail")
                .mimeType("image/png")
                .state(RenditionState.READY)
                .available(true)
                .downloadable(true)
                .contentUrl("/api/v1/documents/" + nodeId + "/thumbnail")
                .sourceStatus("READY")
                .sourceUpdatedAt(java.time.LocalDateTime.of(2026, 3, 18, 10, 15))
                .versionLabel("1.2")
                .sortOrder(1)
                .build()
        ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/renditions", nodeId)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].renditionId").value("preview"))
            .andExpect(jsonPath("$.content[0].label").value("Preview"))
            .andExpect(jsonPath("$.content[0].status").value("READY"))
            .andExpect(jsonPath("$.content[0].available").value(true))
            .andExpect(jsonPath("$.content[0].mimeType").value("application/json"))
            .andExpect(jsonPath("$.content[0].url").value("/api/v1/documents/" + nodeId + "/preview"))
            .andExpect(jsonPath("$.content[0].downloadable").value(false))
            .andExpect(jsonPath("$.content[0].currentVersionLabel").value("1.2"))
            .andExpect(jsonPath("$.content[1].renditionId").value("thumbnail"))
            .andExpect(jsonPath("$.content[1].label").value("Thumbnail"))
            .andExpect(jsonPath("$.content[1].status").value("READY"))
            .andExpect(jsonPath("$.content[1].available").value(true))
            .andExpect(jsonPath("$.content[1].mimeType").value("image/png"))
            .andExpect(jsonPath("$.content[1].url").value("/api/v1/documents/" + nodeId + "/thumbnail"))
            .andExpect(jsonPath("$.content[1].downloadable").value(true));
    }

    @Test
    @DisplayName("Node relation rendition returns failure details for missing preview state")
    void getNodeRelationRenditionShouldReturnFailureDetailsWhenPreviewUnavailable() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setName("broken.pdf");
        document.setPath("/workspace/broken.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Hash mismatch");
        document.setPreviewAvailable(false);

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(renditionResourceService.listForDocument(document)).thenReturn(List.of(
            RenditionResource.builder()
                .document(document)
                .renditionKey("preview")
                .label("Preview")
                .mimeType("application/json")
                .state(RenditionState.FAILED)
                .available(false)
                .downloadable(false)
                .contentUrl("/api/v1/documents/" + nodeId + "/preview")
                .sourceStatus("FAILED")
                .errorReason("Hash mismatch")
                .errorCategory("CONTENT")
                .sortOrder(0)
                .build(),
            RenditionResource.builder()
                .document(document)
                .renditionKey("thumbnail")
                .label("Thumbnail")
                .mimeType("image/png")
                .state(RenditionState.REGISTERED)
                .available(false)
                .downloadable(true)
                .contentUrl("/api/v1/documents/" + nodeId + "/thumbnail")
                .sourceStatus("FAILED")
                .sortOrder(1)
                .build()
        ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/renditions/{renditionId}", nodeId, "preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.renditionId").value("preview"))
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.failureReason").value("Hash mismatch"))
            .andExpect(jsonPath("$.failureCategory").exists())
            .andExpect(jsonPath("$.url").value("/api/v1/documents/" + nodeId + "/preview"));
    }

    @Test
    @DisplayName("Node relation renditions returns empty page for folders")
    void getNodeRelationRenditionsShouldReturnEmptyForFolder() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(nodeId);
        folder.setName("workspace");
        folder.setPath("/workspace");

        when(nodeService.getNode(nodeId)).thenReturn(folder);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/renditions", nodeId)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Node rendition relation summary reuses rendition-resource summary for preview state")
    void getNodeRenditionRelationSummaryShouldReuseRenditionResourceSummary() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setName("report.pdf");
        document.setPath("/workspace/report.pdf");

        when(nodeService.getNode(nodeId)).thenReturn(document);
        when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                nodeId,
                true,
                "FAILED",
                false,
                "LibreOffice timeout",
                "TRANSFORM",
                java.time.LocalDateTime.of(2026, 3, 28, 10, 20),
                "2.4"
            )
        );

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/renditions/summary", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document").value(true))
            .andExpect(jsonPath("$.previewStatus").value("FAILED"))
            .andExpect(jsonPath("$.renditionAvailable").value(false))
            .andExpect(jsonPath("$.previewFailureReason").value("LibreOffice timeout"))
            .andExpect(jsonPath("$.previewFailureCategory").value("TRANSFORM"))
            .andExpect(jsonPath("$.currentVersionLabel").value("2.4"));
    }
}
