package com.ecm.core.controller;

import com.ecm.core.dto.CommentDto;
import com.ecm.core.dto.CommentStatisticsDto;
import com.ecm.core.entity.Node;
import com.ecm.core.model.Comment;
import com.ecm.core.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CommentService commentService;

    @InjectMocks
    private CommentController commentController;

    @InjectMocks
    private UserCommentController userCommentController;

    private UUID nodeId;
    private UUID commentId;
    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(commentController, userCommentController).build();
        nodeId = UUID.randomUUID();
        commentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Add comment returns mapped payload")
    void addCommentReturnsMappedPayload() throws Exception {
        Comment comment = createComment(commentId, "hello @bob", "alice");
        comment.setMentionedUsers(Set.of("bob"));
        Mockito.when(commentService.addComment(nodeId.toString(), "hello @bob", "parent-1"))
            .thenReturn(comment);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/comments", nodeId)
                .contentType("application/json")
                .content("{\"content\":\"hello @bob\",\"parentCommentId\":\"parent-1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(commentId.toString()))
            .andExpect(jsonPath("$.content").value("hello @bob"))
            .andExpect(jsonPath("$.author").value("alice"))
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.nodeName").value("Sample Document"))
            .andExpect(jsonPath("$.nodeType").value("DOCUMENT"))
            .andExpect(jsonPath("$.mentionedUsers", hasSize(1)))
            .andExpect(jsonPath("$.mentionedUsers[0]").value("bob"));

        Mockito.verify(commentService).addComment(nodeId.toString(), "hello @bob", "parent-1");
    }

    @Test
    @DisplayName("List node comments returns paged DTOs")
    void getNodeCommentsReturnsPagedDtos() throws Exception {
        Comment comment = createComment(commentId, "root comment", "alice");
        Mockito.when(commentService.getNodeComments(nodeId.toString(), PageRequest.of(1, 5)))
            .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/comments", nodeId)
                .param("page", "1")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].id").value(commentId.toString()))
            .andExpect(jsonPath("$.content[0].content").value("root comment"))
            .andExpect(jsonPath("$.content[0].nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.content[0].nodeType").value("DOCUMENT"))
            .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    @DisplayName("Comment tree returns nested replies")
    void getCommentTreeReturnsNestedReplies() throws Exception {
        Comment root = createComment(commentId, "root", "alice");
        Comment reply = createComment(UUID.randomUUID(), "reply", "bob");
        reply.setParentComment(root);
        root.setReplies(List.of(reply));

        CommentService.CommentTreeNode rootNode = new CommentService.CommentTreeNode();
        rootNode.setComment(root);
        CommentService.CommentTreeNode replyNode = new CommentService.CommentTreeNode();
        replyNode.setComment(reply);
        rootNode.setReplies(List.of(replyNode));

        Mockito.when(commentService.getCommentTree(nodeId.toString())).thenReturn(List.of(rootNode));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/comments/tree", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(commentId.toString()))
            .andExpect(jsonPath("$[0].nodeName").value("Sample Document"))
            .andExpect(jsonPath("$[0].replies", hasSize(1)))
            .andExpect(jsonPath("$[0].replies[0].content").value("reply"));
    }

    @Test
    @DisplayName("Search comments returns matched DTOs")
    void searchCommentsReturnsDtos() throws Exception {
        Comment comment = createComment(commentId, "alpha beta", "alice");
        Mockito.when(commentService.searchComments(nodeId.toString(), "alpha"))
            .thenReturn(List.of(comment));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/comments/search", nodeId)
                .param("q", "alpha"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].content").value("alpha beta"));
    }

    @Test
    @DisplayName("Comment statistics returns mapped payload")
    void getCommentStatisticsReturnsMappedPayload() throws Exception {
        CommentService.CommentStatistics stats = new CommentService.CommentStatistics();
        stats.setNodeId(nodeId.toString());
        stats.setTotalComments(3L);
        stats.setUniqueCommenters(2L);
        stats.setTopCommenters(Map.of("alice", 2L, "bob", 1L));
        Mockito.when(commentService.getCommentStatistics(nodeId.toString())).thenReturn(stats);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/comments/statistics", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.totalComments").value(3))
            .andExpect(jsonPath("$.uniqueCommenters").value(2))
            .andExpect(jsonPath("$.topCommenters.alice").value(2));
    }

    @Test
    @DisplayName("Edit comment returns updated DTO")
    void editCommentReturnsUpdatedDto() throws Exception {
        Comment updated = createComment(commentId, "updated", "alice");
        updated.setEdited(new Date());
        updated.setEditor("alice");
        Mockito.when(commentService.editComment(commentId.toString(), "updated"))
            .thenReturn(updated);

        mockMvc.perform(put("/api/v1/comments/{commentId}", commentId)
                .contentType("application/json")
                .content("{\"content\":\"updated\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(commentId.toString()))
            .andExpect(jsonPath("$.content").value("updated"))
            .andExpect(jsonPath("$.editor").value("alice"));
    }

    @Test
    @DisplayName("Reaction endpoints delegate to service")
    void reactionEndpointsDelegateToService() throws Exception {
        mockMvc.perform(post("/api/v1/comments/{commentId}/reactions", commentId)
                .contentType("application/json")
                .content("{\"reactionType\":\"like\"}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/comments/{commentId}/reactions", commentId))
            .andExpect(status().isNoContent());

        Mockito.verify(commentService).addReaction(commentId.toString(), "like");
        Mockito.verify(commentService).removeReaction(commentId.toString());
    }

    @Test
    @DisplayName("Delete comment delegates to service")
    void deleteCommentDelegatesToService() throws Exception {
        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId))
            .andExpect(status().isNoContent());

        Mockito.verify(commentService).deleteComment(commentId.toString());
    }

    @Test
    @DisplayName("User comment endpoints map page payloads")
    void userCommentEndpointsMapPagePayloads() throws Exception {
        Comment comment = createComment(commentId, "user comment", "alice");
        Mockito.when(commentService.getUserComments("alice", PageRequest.of(0, 3)))
            .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 3), 1));
        Mockito.when(commentService.getMentionedComments("alice", PageRequest.of(0, 3)))
            .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 3), 1));

        mockMvc.perform(get("/api/v1/users/{username}/comments", "alice")
                .param("page", "0")
                .param("size", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].author").value("alice"))
            .andExpect(jsonPath("$.content[0].nodeName").value("Sample Document"));

        mockMvc.perform(get("/api/v1/users/{username}/mentioned-comments", "alice")
                .param("page", "0")
                .param("size", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].content").value("user comment"))
            .andExpect(jsonPath("$.content[0].nodeType").value("DOCUMENT"));
    }

    private Comment createComment(UUID id, String content, String author) {
        Comment comment = new Comment();
        Node documentNode = Mockito.mock(Node.class);
        Mockito.when(documentNode.getId()).thenReturn(nodeId);
        Mockito.when(documentNode.getName()).thenReturn("Sample Document");
        Mockito.when(documentNode.getNodeType()).thenReturn(Node.NodeType.DOCUMENT);
        comment.setId(id);
        comment.setContent(content);
        comment.setAuthor(author);
        comment.setNode(documentNode);
        comment.setCreated(new Date(1_700_000_000_000L));
        return comment;
    }
}
