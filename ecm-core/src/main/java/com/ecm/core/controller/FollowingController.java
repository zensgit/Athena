package com.ecm.core.controller;

import com.ecm.core.entity.FollowTargetType;
import com.ecm.core.service.FollowingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/followings", "/api/v1/followings"})
@RequiredArgsConstructor
@Tag(name = "Following", description = "Follow users, sites, and nodes for personalized activity feeds")
public class FollowingController {

    private final FollowingService followingService;

    @GetMapping
    @Operation(summary = "List current user subscriptions")
    public ResponseEntity<List<FollowingService.FollowSubscriptionDto>> list() {
        return ResponseEntity.ok(followingService.listCurrentUserSubscriptions());
    }

    @GetMapping("/check")
    @Operation(summary = "Check whether current user follows a target")
    public ResponseEntity<Boolean> check(
        @RequestParam FollowTargetType targetType,
        @RequestParam String targetId
    ) {
        return ResponseEntity.ok(followingService.isFollowing(targetType, targetId));
    }

    @PostMapping
    @Operation(summary = "Follow a target")
    public ResponseEntity<FollowingService.FollowSubscriptionDto> follow(@RequestBody FollowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(followingService.follow(request.targetType(), request.targetId()));
    }

    @DeleteMapping("/{targetType}/{targetId}")
    @Operation(summary = "Unfollow a target")
    public ResponseEntity<Void> unfollow(
        @PathVariable FollowTargetType targetType,
        @PathVariable String targetId
    ) {
        followingService.unfollow(targetType, targetId);
        return ResponseEntity.noContent().build();
    }

    public record FollowRequest(
        FollowTargetType targetType,
        String targetId
    ) {}
}
