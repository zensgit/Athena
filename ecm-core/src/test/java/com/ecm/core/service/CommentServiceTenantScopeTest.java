package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.event.CommentAddedEvent;
import com.ecm.core.exception.NodeNotFoundException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.model.Comment;
import com.ecm.core.repository.CommentRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
class CommentServiceTenantScopeTest {

    @Mock private CommentRepository commentRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RuleEngineService ruleEngineService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService();
        ReflectionTestUtils.setField(service, "commentRepository", commentRepository);
        ReflectionTestUtils.setField(service, "nodeRepository", nodeRepository);
        ReflectionTestUtils.setField(service, "securityService", securityService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "ruleEngineService", ruleEngineService);
        ReflectionTestUtils.setField(service, "tenantWorkspaceScopeService", tenantWorkspaceScopeService);
        ReflectionTestUtils.setField(service, "rulesEnabled", false);
    }

    @Test
    @DisplayName("getNodeComments hides nodes outside current tenant workspace")
    void getNodeCommentsHidesForeignTenantNode() {
        UUID nodeId = UUID.randomUUID();
        Document hiddenNode = document(nodeId, "/foreign/doc");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(hiddenNode));
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);

        assertThrows(NodeNotFoundException.class,
            () -> service.getNodeComments(nodeId.toString(), PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("editComment hides comment when backing node is outside current tenant workspace")
    void editCommentHidesForeignTenantComment() {
        UUID commentId = UUID.randomUUID();
        Comment hiddenComment = comment(commentId, document(UUID.randomUUID(), "/foreign/doc"));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(hiddenComment));
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
            () -> service.editComment(commentId.toString(), "updated"));
    }

    @Test
    @DisplayName("getUserComments filters hidden tenant comments from page content")
    void getUserCommentsFiltersHiddenComments() {
        PageRequest pageable = PageRequest.of(0, 10);
        Comment visible = comment(UUID.randomUUID(), document(UUID.randomUUID(), "/tenant/doc"));
        Comment hidden = comment(UUID.randomUUID(), document(UUID.randomUUID(), "/foreign/doc"));
        when(commentRepository.findByAuthorAndDeletedFalseOrderByCreatedDesc("alice", pageable))
            .thenReturn(new PageImpl<>(List.of(visible, hidden), pageable, 2));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/tenant/doc")).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);

        Page<Comment> result = service.getUserComments("alice", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("/tenant/doc", result.getContent().get(0).getNode().getPath());
    }

    private Comment comment(UUID id, Document node) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setNode(node);
        comment.setAuthor("alice");
        comment.setContent("hello");
        return comment;
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
