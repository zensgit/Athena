package com.ecm.core.controller;

import com.ecm.core.entity.Activity;
import com.ecm.core.entity.Notification;
import com.ecm.core.service.NotificationInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/notifications", "/api/v1/notifications"})
@Tag(name = "Notifications", description = "User notification inbox")
public class NotificationController {

    private final NotificationInboxService inboxService;

    @GetMapping
    @Operation(summary = "Get notification inbox")
    public ResponseEntity<Page<NotificationDto>> getInbox(Pageable pageable) {
        return ResponseEntity.ok(inboxService.getInbox(pageable).map(NotificationDto::from));
    }

    @GetMapping("/unread")
    @Operation(summary = "Get unread notifications")
    public ResponseEntity<Page<NotificationDto>> getUnread(Pageable pageable) {
        return ResponseEntity.ok(inboxService.getUnread(pageable).map(NotificationDto::from));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", inboxService.getUnreadCount()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<NotificationDto> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(NotificationDto.from(inboxService.markRead(id)));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        return ResponseEntity.ok(Map.of("marked", inboxService.markAllRead()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification")
    public ResponseEntity<Void> deleteNotification(@PathVariable UUID id) {
        inboxService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    public record NotificationDto(
        UUID id,
        String activityType,
        String actorUserId,
        String siteId,
        UUID nodeId,
        String nodeName,
        Map<String, Object> summary,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
    ) {
        static NotificationDto from(Notification n) {
            Activity a = n.getActivity();
            return new NotificationDto(
                n.getId(),
                a != null ? a.getActivityType() : null,
                a != null ? a.getUserId() : null,
                a != null ? a.getSiteId() : null,
                a != null ? a.getNodeId() : null,
                a != null ? a.getNodeName() : null,
                a != null ? a.getSummary() : Map.of(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt()
            );
        }
    }
}
