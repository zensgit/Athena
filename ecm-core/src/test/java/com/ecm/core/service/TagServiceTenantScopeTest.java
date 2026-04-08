package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.NodeNotFoundException;
import com.ecm.core.model.Tag;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTenantScopeTest {

    @Mock private TagRepository tagRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private RuleEngineService ruleEngineService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private TagService service;

    @BeforeEach
    void setUp() {
        service = new TagService();
        ReflectionTestUtils.setField(service, "tagRepository", tagRepository);
        ReflectionTestUtils.setField(service, "nodeRepository", nodeRepository);
        ReflectionTestUtils.setField(service, "securityService", securityService);
        ReflectionTestUtils.setField(service, "ruleEngineService", ruleEngineService);
        ReflectionTestUtils.setField(service, "tenantWorkspaceScopeService", tenantWorkspaceScopeService);
        ReflectionTestUtils.setField(service, "rulesEnabled", false);
    }

    @Test
    @DisplayName("addTagToNode hides nodes outside current tenant workspace")
    void addTagToNodeHidesForeignTenantNode() {
        UUID nodeId = UUID.randomUUID();
        Document hidden = document(nodeId, "/foreign/doc");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(hidden));
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);

        assertThrows(NodeNotFoundException.class, () -> service.addTagToNode(nodeId.toString(), "finance"));
    }

    @Test
    @DisplayName("findNodesByTag filters hidden tenant nodes")
    void findNodesByTagFiltersHiddenNodes() {
        PageRequest pageable = PageRequest.of(0, 10);
        Tag tag = new Tag();
        tag.setName("finance");
        Document visible = document(UUID.randomUUID(), "/tenant/doc");
        Document hidden = document(UUID.randomUUID(), "/foreign/doc");
        when(tagRepository.findByName("finance")).thenReturn(Optional.of(tag));
        when(nodeRepository.findByTagsContainingAndDeletedFalseAndArchiveStatus(tag, Node.ArchiveStatus.LIVE, pageable))
            .thenReturn(new PageImpl<>(List.of(visible, hidden), pageable, 2));
        when(tenantWorkspaceScopeService.isPathVisible("/tenant/doc")).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);
        when(securityService.hasPermission(visible, com.ecm.core.entity.Permission.PermissionType.READ)).thenReturn(true);

        Page<Node> result = service.findNodesByTag("finance", pageable);

        assertEquals(1, result.getContent().size());
        assertEquals("/tenant/doc", result.getContent().get(0).getPath());
    }

    private Document document(UUID id, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName("doc.txt");
        document.setPath(path);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        document.setDeleted(false);
        return document;
    }
}
