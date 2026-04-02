package com.ecm.core.controller;

import com.ecm.core.dto.CommentDto;
import com.ecm.core.dto.CommentReactionRequest;
import com.ecm.core.dto.CommentStatisticsDto;
import com.ecm.core.dto.CreateCommentRequest;
import com.ecm.core.dto.UpdateCommentRequest;
import com.ecm.core.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api", "/api/v1"})
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Comment APIs")
public class CommentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommentService commentService;

    @PostMapping("/nodes/{nodeId}/comments")
    @Operation(summary = "Add comment", description = "Add a new comment to a node")
    public ResponseEntity<CommentDto> addComment(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestBody CreateCommentRequest request
    ) {
        var comment = commentService.addComment(
            nodeId.toString(),
            request.content(),
            request.parentCommentId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentDto.from(comment));
    }

    @GetMapping("/nodes/{nodeId}/comments")
    @Operation(summary = "List node comments", description = "Get top-level comments for a node")
    public ResponseEntity<Page<CommentDto>> getNodeComments(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = pageRequest(page, size);
        Page<CommentDto> mapped = commentService.getNodeComments(nodeId.toString(), pageable)
            .map(CommentDto::from);
        return ResponseEntity.ok(mapped);
    }

    @GetMapping("/nodes/{nodeId}/comments/tree")
    @Operation(summary = "Get comment tree", description = "Get comment tree for a node")
    public ResponseEntity<List<CommentDto>> getCommentTree(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId
    ) {
        return ResponseEntity.ok(commentService.getCommentTree(nodeId.toString()).stream()
            .map(CommentDto::from)
            .toList());
    }

    @GetMapping("/nodes/{nodeId}/comments/search")
    @Operation(summary = "Search comments", description = "Search comments for a node")
    public ResponseEntity<List<CommentDto>> searchComments(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestParam(name = "q", required = false, defaultValue = "") String query
    ) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(commentService.searchComments(nodeId.toString(), normalizedQuery).stream()
            .map(CommentDto::from)
            .toList());
    }

    @GetMapping("/nodes/{nodeId}/comments/statistics")
    @Operation(summary = "Get comment statistics", description = "Get comment statistics for a node")
    public ResponseEntity<CommentStatisticsDto> getCommentStatistics(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId
    ) {
        return ResponseEntity.ok(CommentStatisticsDto.from(commentService.getCommentStatistics(nodeId.toString())));
    }

    @PutMapping("/comments/{commentId}")
    @Operation(summary = "Edit comment", description = "Edit an existing comment")
    public ResponseEntity<CommentDto> editComment(
        @Parameter(description = "Comment ID") @PathVariable UUID commentId,
        @RequestBody UpdateCommentRequest request
    ) {
        return ResponseEntity.ok(CommentDto.from(commentService.editComment(commentId.toString(), request.content())));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Delete comment", description = "Delete a comment")
    public ResponseEntity<Void> deleteComment(
        @Parameter(description = "Comment ID") @PathVariable UUID commentId
    ) {
        commentService.deleteComment(commentId.toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/reactions")
    @Operation(summary = "Add reaction", description = "Add a reaction to a comment")
    public ResponseEntity<Void> addReaction(
        @Parameter(description = "Comment ID") @PathVariable UUID commentId,
        @RequestBody CommentReactionRequest request
    ) {
        commentService.addReaction(commentId.toString(), request.reactionType());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comments/{commentId}/reactions")
    @Operation(summary = "Remove reaction", description = "Remove the current user's reaction from a comment")
    public ResponseEntity<Void> removeReaction(
        @Parameter(description = "Comment ID") @PathVariable UUID commentId
    ) {
        commentService.removeReaction(commentId.toString());
        return ResponseEntity.noContent().build();
    }

    private Pageable pageRequest(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize);
    }
}
