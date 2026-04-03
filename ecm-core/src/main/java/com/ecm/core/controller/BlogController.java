package com.ecm.core.controller;

import com.ecm.core.entity.BlogPost;
import com.ecm.core.entity.BlogPost.BlogStatus;
import com.ecm.core.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/sites/{siteId}/blog", "/api/v1/sites/{siteId}/blog"})
@Tag(name = "Blog", description = "Site blog posts")
public class BlogController {

    private final BlogService blogService;

    @GetMapping("/posts")
    @Operation(summary = "List blog posts for a site")
    public ResponseEntity<Page<BlogPostDto>> listPosts(
            @PathVariable String siteId,
            @RequestParam(required = false) BlogStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(blogService.listPosts(siteId, status, pageable).map(BlogPostDto::from));
    }

    @GetMapping("/posts/drafts")
    @Operation(summary = "List draft posts")
    public ResponseEntity<Page<BlogPostDto>> listDrafts(@PathVariable String siteId, Pageable pageable) {
        return ResponseEntity.ok(blogService.listDrafts(siteId, pageable).map(BlogPostDto::from));
    }

    @PostMapping("/posts")
    @Operation(summary = "Create a blog post (draft)")
    public ResponseEntity<BlogPostDto> createPost(
            @PathVariable String siteId,
            @RequestBody CreateBlogPostRequest request) {
        BlogPost post = blogService.createPost(siteId, request.title(), request.content(), request.tags());
        return ResponseEntity.status(HttpStatus.CREATED).body(BlogPostDto.from(post));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "Get a blog post")
    public ResponseEntity<BlogPostDto> getPost(@PathVariable String siteId, @PathVariable UUID postId) {
        return ResponseEntity.ok(BlogPostDto.from(blogService.getPost(postId)));
    }

    @PutMapping("/posts/{postId}")
    @Operation(summary = "Update a blog post")
    public ResponseEntity<BlogPostDto> updatePost(
            @PathVariable String siteId,
            @PathVariable UUID postId,
            @RequestBody UpdateBlogPostRequest request) {
        BlogPost post = blogService.updatePost(postId, request.title(), request.content(), request.tags());
        return ResponseEntity.ok(BlogPostDto.from(post));
    }

    @PostMapping("/posts/{postId}/publish")
    @Operation(summary = "Publish a draft blog post")
    public ResponseEntity<BlogPostDto> publish(@PathVariable String siteId, @PathVariable UUID postId) {
        return ResponseEntity.ok(BlogPostDto.from(blogService.publish(postId)));
    }

    @PostMapping("/posts/{postId}/unpublish")
    @Operation(summary = "Revert a published post to draft")
    public ResponseEntity<BlogPostDto> unpublish(@PathVariable String siteId, @PathVariable UUID postId) {
        return ResponseEntity.ok(BlogPostDto.from(blogService.unpublish(postId)));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "Delete a blog post")
    public ResponseEntity<Void> deletePost(@PathVariable String siteId, @PathVariable UUID postId) {
        blogService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }

    // ---- DTOs ---------------------------------------------------------------

    public record CreateBlogPostRequest(String title, String content, List<String> tags) {}
    public record UpdateBlogPostRequest(String title, String content, List<String> tags) {}

    public record BlogPostDto(UUID id, String siteId, String title, String content, BlogStatus status,
                              LocalDateTime publishedDate, List<String> tags,
                              String createdBy, LocalDateTime createdDate) {
        static BlogPostDto from(BlogPost p) {
            return new BlogPostDto(p.getId(), p.getSiteId(), p.getTitle(), p.getContent(),
                p.getStatus(), p.getPublishedDate(), p.getTags(),
                p.getCreatedBy(), p.getCreatedDate());
        }
    }
}
