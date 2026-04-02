package com.ecm.core.controller;

import com.ecm.core.entity.Activity;
import com.ecm.core.service.ActivityService;
import com.ecm.core.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/activities", "/api/v1/activities"})
@Tag(name = "Activity Feed", description = "User and site activity streams")
public class ActivityController {

    private final ActivityService activityService;
    private final SecurityService securityService;

    @GetMapping
    @Operation(summary = "Get global activity feed")
    public ResponseEntity<Page<ActivityDto>> getGlobalFeed(Pageable pageable) {
        return ResponseEntity.ok(activityService.getGlobalFeed(pageable).map(ActivityDto::from));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user activity feed")
    public ResponseEntity<Page<ActivityDto>> getUserFeed(
            @Parameter(description = "User ID / username") @PathVariable String userId,
            Pageable pageable) {
        return ResponseEntity.ok(activityService.getUserFeed(userId, pageable).map(ActivityDto::from));
    }

    @GetMapping("/sites/{siteId}")
    @Operation(summary = "Get site activity feed")
    public ResponseEntity<Page<ActivityDto>> getSiteFeed(
            @Parameter(description = "Site ID") @PathVariable String siteId,
            Pageable pageable) {
        return ResponseEntity.ok(activityService.getSiteFeed(siteId, pageable).map(ActivityDto::from));
    }

    @GetMapping("/following")
    @Operation(summary = "Get personalized feed for followed users, sites, and nodes")
    public ResponseEntity<Page<ActivityDto>> getFollowingFeed(Pageable pageable) {
        return ResponseEntity.ok(
            activityService.getFollowingFeed(securityService.getCurrentUser(), pageable).map(ActivityDto::from)
        );
    }

    @GetMapping("/nodes/{nodeId}")
    @Operation(summary = "Get node activity feed")
    public ResponseEntity<Page<ActivityDto>> getNodeFeed(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            Pageable pageable) {
        return ResponseEntity.ok(activityService.getNodeFeed(nodeId, pageable).map(ActivityDto::from));
    }

    @PostMapping
    @Operation(summary = "Post an activity (internal/admin)")
    public ResponseEntity<ActivityDto> postActivity(@RequestBody PostActivityRequest request) {
        Activity activity = activityService.postActivity(
            request.activityType(), request.userId(), request.siteId(),
            request.nodeId(), request.nodeName(), request.summary()
        );
        return ResponseEntity.ok(ActivityDto.from(activity));
    }

    // ---- DTOs ---------------------------------------------------------------

    public record ActivityDto(
        UUID id,
        String activityType,
        String userId,
        String siteId,
        UUID nodeId,
        String nodeName,
        Map<String, Object> summary,
        LocalDateTime postedAt
    ) {
        static ActivityDto from(Activity a) {
            return new ActivityDto(
                a.getId(), a.getActivityType(), a.getUserId(), a.getSiteId(),
                a.getNodeId(), a.getNodeName(), a.getSummary(), a.getPostedAt()
            );
        }
    }

    public record PostActivityRequest(
        String activityType,
        String userId,
        String siteId,
        UUID nodeId,
        String nodeName,
        Map<String, Object> summary
    ) {}
}
