package com.ecm.core.service;

import com.ecm.core.entity.Activity;
import com.ecm.core.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityService {

    private static final UUID EMPTY_NODE_SENTINEL = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ActivityRepository activityRepository;
    private final FollowingService followingService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private NotificationInboxService notificationInboxService;

    /**
     * Post a new activity entry.
     */
    @Transactional
    public Activity postActivity(String activityType, String userId, String siteId,
                                  UUID nodeId, String nodeName, Map<String, Object> summary) {
        Activity activity = new Activity();
        activity.setActivityType(activityType);
        activity.setUserId(userId);
        activity.setSiteId(siteId);
        activity.setNodeId(nodeId);
        activity.setNodeName(nodeName);
        if (summary != null) {
            activity.setSummary(summary);
        }
        Activity saved = activityRepository.save(activity);
        // route to follower inboxes
        if (notificationInboxService != null) {
            try {
                notificationInboxService.routeActivityToFollowers(saved);
            } catch (Exception e) {
                log.warn("Failed to route notifications for activity {}: {}", saved.getId(), e.getMessage());
            }
        }
        return saved;
    }

    /**
     * Get activity feed for a specific user.
     */
    @Transactional(readOnly = true)
    public Page<Activity> getUserFeed(String userId, Pageable pageable) {
        return filterVisibleActivities(activityRepository.findByUserIdOrderByPostedAtDesc(userId, Pageable.unpaged()), pageable);
    }

    /**
     * Get activity feed for a site.
     */
    @Transactional(readOnly = true)
    public Page<Activity> getSiteFeed(String siteId, Pageable pageable) {
        return filterVisibleActivities(activityRepository.findBySiteIdOrderByPostedAtDesc(siteId, Pageable.unpaged()), pageable);
    }

    /**
     * Get personalized activity feed based on followed users, sites, and nodes.
     */
    @Transactional(readOnly = true)
    public Page<Activity> getFollowingFeed(String userId, Pageable pageable) {
        FollowingService.FollowingTargets targets = followingService.getFollowingTargets(userId);
        boolean includeUsers = !targets.userIds().isEmpty();
        boolean includeSites = !targets.siteIds().isEmpty();
        boolean includeNodes = !targets.nodeIds().isEmpty();

        if (!includeUsers && !includeSites && !includeNodes) {
            return Page.empty(pageable);
        }

        return filterVisibleActivities(activityRepository.findFollowingFeed(
            includeUsers,
            includeUsers ? targets.userIds() : List.of("__no-followed-user__"),
            includeSites,
            includeSites ? targets.siteIds() : List.of("__no-followed-site__"),
            includeNodes,
            includeNodes ? targets.nodeIds() : List.of(EMPTY_NODE_SENTINEL),
            Pageable.unpaged()
        ), pageable);
    }

    /**
     * Get global activity feed (all users).
     */
    @Transactional(readOnly = true)
    public Page<Activity> getGlobalFeed(Pageable pageable) {
        return filterVisibleActivities(activityRepository.findAllByOrderByPostedAtDesc(Pageable.unpaged()), pageable);
    }

    /**
     * Get activities for a specific node.
     */
    @Transactional(readOnly = true)
    public Page<Activity> getNodeFeed(UUID nodeId, Pageable pageable) {
        return filterVisibleActivities(activityRepository.findByNodeIdOrderByPostedAtDesc(nodeId, Pageable.unpaged()), pageable);
    }

    /**
     * Cleanup old activities (older than 90 days). Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldActivities() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int deleted = activityRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} activities older than {}", deleted, cutoff);
        }
    }

    private Page<Activity> filterVisibleActivities(Page<Activity> source, Pageable pageable) {
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        List<Activity> visible = source.getContent().stream()
            .filter(activity -> tenantWorkspaceScopeService.isActivityVisible(activity, tenantRootPath))
            .toList();
        return slice(visible, pageable);
    }

    private Page<Activity> slice(List<Activity> activities, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(activities);
        }

        int fromIndex = Math.toIntExact(pageable.getOffset());
        if (fromIndex >= activities.size()) {
            return new PageImpl<>(List.of(), pageable, activities.size());
        }
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), activities.size());
        return new PageImpl<>(activities.subList(fromIndex, toIndex), pageable, activities.size());
    }
}
