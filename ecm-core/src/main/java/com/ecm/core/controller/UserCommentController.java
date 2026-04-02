package com.ecm.core.controller;

import com.ecm.core.dto.CommentDto;
import com.ecm.core.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/users", "/api/v1/users"})
@RequiredArgsConstructor
@Tag(name = "User Comments", description = "Comment APIs scoped to a user")
public class UserCommentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommentService commentService;

    @GetMapping("/{username}/comments")
    @Operation(summary = "Get user comments", description = "Get comments authored by a user")
    public ResponseEntity<Page<CommentDto>> getUserComments(
        @Parameter(description = "Username") @PathVariable String username,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(commentService.getUserComments(username, pageRequest(page, size))
            .map(CommentDto::from));
    }

    @GetMapping("/{username}/mentioned-comments")
    @Operation(summary = "Get mentioned comments", description = "Get comments mentioning a user")
    public ResponseEntity<Page<CommentDto>> getMentionedComments(
        @Parameter(description = "Username") @PathVariable String username,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(commentService.getMentionedComments(username, pageRequest(page, size))
            .map(CommentDto::from));
    }

    private Pageable pageRequest(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize);
    }
}
