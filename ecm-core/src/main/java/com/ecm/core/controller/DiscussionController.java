package com.ecm.core.controller;

import com.ecm.core.entity.DiscussionReply;
import com.ecm.core.entity.DiscussionTopic;
import com.ecm.core.entity.DiscussionTopic.TopicStatus;
import com.ecm.core.service.DiscussionService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/sites/{siteId}/discussions", "/api/v1/sites/{siteId}/discussions"})
@Tag(name = "Discussions", description = "Site discussion forums")
public class DiscussionController {

    private final DiscussionService discussionService;

    // ---- topics -------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List discussion topics for a site")
    public ResponseEntity<Page<TopicDto>> listTopics(
            @PathVariable String siteId,
            @RequestParam(required = false) TopicStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(discussionService.listTopics(siteId, status, pageable).map(TopicDto::from));
    }

    @PostMapping
    @Operation(summary = "Create a discussion topic")
    public ResponseEntity<TopicDto> createTopic(
            @PathVariable String siteId,
            @RequestBody CreateTopicRequest request) {
        DiscussionTopic topic = discussionService.createTopic(
            siteId, request.title(), request.content(), request.tags());
        return ResponseEntity.status(HttpStatus.CREATED).body(TopicDto.from(topic));
    }

    @GetMapping("/{topicId}")
    @Operation(summary = "Get a discussion topic")
    public ResponseEntity<TopicDto> getTopic(
            @PathVariable String siteId,
            @PathVariable UUID topicId) {
        return ResponseEntity.ok(TopicDto.from(discussionService.getTopic(topicId)));
    }

    @PutMapping("/{topicId}")
    @Operation(summary = "Update a discussion topic")
    public ResponseEntity<TopicDto> updateTopic(
            @PathVariable String siteId,
            @PathVariable UUID topicId,
            @RequestBody UpdateTopicRequest request) {
        DiscussionTopic topic = discussionService.updateTopic(topicId, request.title(), request.content(), request.status());
        return ResponseEntity.ok(TopicDto.from(topic));
    }

    @DeleteMapping("/{topicId}")
    @Operation(summary = "Delete a discussion topic")
    public ResponseEntity<Void> deleteTopic(
            @PathVariable String siteId,
            @PathVariable UUID topicId) {
        discussionService.deleteTopic(topicId);
        return ResponseEntity.noContent().build();
    }

    // ---- replies ------------------------------------------------------------

    @GetMapping("/{topicId}/replies")
    @Operation(summary = "List replies for a topic")
    public ResponseEntity<Page<ReplyDto>> listReplies(
            @PathVariable String siteId,
            @PathVariable UUID topicId,
            Pageable pageable) {
        return ResponseEntity.ok(discussionService.listReplies(topicId, pageable).map(ReplyDto::from));
    }

    @PostMapping("/{topicId}/replies")
    @Operation(summary = "Reply to a topic")
    public ResponseEntity<ReplyDto> createReply(
            @PathVariable String siteId,
            @PathVariable UUID topicId,
            @RequestBody CreateReplyRequest request) {
        DiscussionReply reply = discussionService.createReply(topicId, request.content(), request.parentReplyId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReplyDto.from(reply));
    }

    @PutMapping("/{topicId}/replies/{replyId}")
    @Operation(summary = "Edit a reply")
    public ResponseEntity<ReplyDto> updateReply(
            @PathVariable String siteId,
            @PathVariable UUID topicId,
            @PathVariable UUID replyId,
            @RequestBody UpdateReplyRequest request) {
        return ResponseEntity.ok(ReplyDto.from(discussionService.updateReply(replyId, request.content())));
    }

    @DeleteMapping("/{topicId}/replies/{replyId}")
    @Operation(summary = "Delete a reply")
    public ResponseEntity<Void> deleteReply(
            @PathVariable String siteId,
            @PathVariable UUID topicId,
            @PathVariable UUID replyId) {
        discussionService.deleteReply(replyId);
        return ResponseEntity.noContent().build();
    }

    // ---- DTOs ---------------------------------------------------------------

    public record CreateTopicRequest(String title, String content, List<String> tags) {}
    public record UpdateTopicRequest(String title, String content, TopicStatus status) {}
    public record CreateReplyRequest(String content, UUID parentReplyId) {}
    public record UpdateReplyRequest(String content) {}

    public record TopicDto(UUID id, String siteId, String title, String content, TopicStatus status,
                           List<String> tags, String createdBy, LocalDateTime createdDate, int replyCount) {
        static TopicDto from(DiscussionTopic t) {
            return new TopicDto(t.getId(), t.getSiteId(), t.getTitle(), t.getContent(), t.getStatus(),
                t.getTags(), t.getCreatedBy(), t.getCreatedDate(),
                t.getReplies() != null ? t.getReplies().size() : 0);
        }
    }

    public record ReplyDto(UUID id, UUID topicId, UUID parentReplyId, String content,
                           String createdBy, LocalDateTime createdDate) {
        static ReplyDto from(DiscussionReply r) {
            return new ReplyDto(r.getId(), r.getTopic().getId(), r.getParentReplyId(),
                r.getContent(), r.getCreatedBy(), r.getCreatedDate());
        }
    }
}
