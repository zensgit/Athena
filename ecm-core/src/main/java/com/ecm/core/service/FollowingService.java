package com.ecm.core.service;

import com.ecm.core.entity.FollowSubscription;
import com.ecm.core.entity.FollowTargetType;
import com.ecm.core.repository.FollowSubscriptionRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.SiteRepository;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowingService {

    private final FollowSubscriptionRepository followSubscriptionRepository;
    private final SecurityService securityService;
    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final NodeRepository nodeRepository;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @Transactional(readOnly = true)
    public List<FollowSubscriptionDto> listCurrentUserSubscriptions() {
        return followSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(securityService.getCurrentUser())
            .stream()
            .filter(this::isSubscriptionVisible)
            .map(FollowingService::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(FollowTargetType targetType, String targetId) {
        String normalizedTargetId = normalizeTargetId(targetType, targetId);
        if (!isTargetVisible(targetType, normalizedTargetId)) {
            return false;
        }
        return followSubscriptionRepository.existsByUserIdAndTargetTypeAndTargetId(
            securityService.getCurrentUser(),
            targetType,
            normalizedTargetId
        );
    }

    @Transactional
    public FollowSubscriptionDto follow(FollowTargetType targetType, String targetId) {
        String userId = securityService.getCurrentUser();
        String normalizedTargetId = normalizeTargetId(targetType, targetId);
        ensureTargetExistsAndVisible(targetType, normalizedTargetId);

        followSubscriptionRepository.findByUserIdAndTargetTypeAndTargetId(userId, targetType, normalizedTargetId)
            .ifPresent(existing -> {
                throw new IllegalStateException("Already following " + targetType + " " + normalizedTargetId);
            });

        FollowSubscription subscription = new FollowSubscription();
        subscription.setUserId(userId);
        subscription.setTargetType(targetType);
        subscription.setTargetId(normalizedTargetId);
        return toDto(followSubscriptionRepository.save(subscription));
    }

    @Transactional
    public void unfollow(FollowTargetType targetType, String targetId) {
        String userId = securityService.getCurrentUser();
        String normalizedTargetId = normalizeTargetId(targetType, targetId);

        if (!followSubscriptionRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, normalizedTargetId)) {
            throw new NoSuchElementException("Follow subscription not found");
        }

        followSubscriptionRepository.deleteByUserIdAndTargetTypeAndTargetId(userId, targetType, normalizedTargetId);
    }

    @Transactional(readOnly = true)
    public FollowingTargets getFollowingTargets(String userId) {
        List<FollowSubscription> subscriptions = followSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<String> followedUsers = subscriptions.stream()
            .filter(this::isSubscriptionVisible)
            .filter(subscription -> subscription.getTargetType() == FollowTargetType.USER)
            .map(FollowSubscription::getTargetId)
            .distinct()
            .toList();

        List<String> followedSites = subscriptions.stream()
            .filter(this::isSubscriptionVisible)
            .filter(subscription -> subscription.getTargetType() == FollowTargetType.SITE)
            .map(FollowSubscription::getTargetId)
            .distinct()
            .toList();

        List<UUID> followedNodes = subscriptions.stream()
            .filter(this::isSubscriptionVisible)
            .filter(subscription -> subscription.getTargetType() == FollowTargetType.NODE)
            .map(subscription -> UUID.fromString(subscription.getTargetId()))
            .distinct()
            .toList();

        return new FollowingTargets(followedUsers, followedSites, followedNodes);
    }

    /**
     * Reverse lookup: find all userIds who follow a given target.
     */
    @Transactional(readOnly = true)
    public List<String> getFollowersOf(String targetType, String targetId) {
        FollowTargetType type = FollowTargetType.valueOf(targetType);
        return followSubscriptionRepository.findByTargetTypeAndTargetId(type, targetId)
            .stream()
            .map(FollowSubscription::getUserId)
            .toList();
    }

    private void ensureTargetExistsAndVisible(FollowTargetType targetType, String targetId) {
        switch (targetType) {
            case USER -> userRepository.findByUsername(targetId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + targetId));
            case SITE -> {
                siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(targetId)
                    .orElseThrow(() -> new NoSuchElementException("Site not found: " + targetId));
                if (!tenantWorkspaceScopeService.isSiteVisible(targetId)) {
                    throw new NoSuchElementException("Site not found: " + targetId);
                }
            }
            case NODE -> {
                UUID nodeId = UUID.fromString(targetId);
                nodeRepository.findById(nodeId)
                    .orElseThrow(() -> new NoSuchElementException("Node not found: " + targetId));
                if (!tenantWorkspaceScopeService.isNodeVisible(nodeId)) {
                    throw new NoSuchElementException("Node not found: " + targetId);
                }
            }
        }
    }

    private boolean isSubscriptionVisible(FollowSubscription subscription) {
        if (subscription == null) {
            return false;
        }
        return isTargetVisible(subscription.getTargetType(), subscription.getTargetId());
    }

    private boolean isTargetVisible(FollowTargetType targetType, String targetId) {
        return switch (targetType) {
            case USER -> true;
            case SITE -> tenantWorkspaceScopeService.isSiteVisible(targetId);
            case NODE -> tenantWorkspaceScopeService.isNodeVisible(UUID.fromString(targetId));
        };
    }

    private String normalizeTargetId(FollowTargetType targetType, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId is required");
        }
        return switch (targetType) {
            case USER, SITE -> targetId.trim();
            case NODE -> UUID.fromString(targetId.trim()).toString();
        };
    }

    public record FollowSubscriptionDto(
        UUID id,
        String userId,
        FollowTargetType targetType,
        String targetId,
        LocalDateTime createdAt
    ) {}

    public record FollowingTargets(
        List<String> userIds,
        List<String> siteIds,
        List<UUID> nodeIds
    ) {}

    private static FollowSubscriptionDto toDto(FollowSubscription subscription) {
        return new FollowSubscriptionDto(
            subscription.getId(),
            subscription.getUserId(),
            subscription.getTargetType(),
            subscription.getTargetId(),
            subscription.getCreatedAt()
        );
    }
}
