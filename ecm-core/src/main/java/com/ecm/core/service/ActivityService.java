package com.ecm.core.service;

import com.ecm.core.entity.Activity;
import com.ecm.core.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;

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
        return activityRepository.save(activity);
    }

    /**
     * Get activity feed for a specific user.
     */
    @Transactional(readOnly = true)
    public Page<Activity> getUserFeed(String userId, Pageable pageable) {
        return activityRepository.findByUserIdOrderByPostedAtDesc(userId, pageable);
    }

    /**
     * Get activity feed for a site.
     */
    @Transactional(readOnly = true)
    public Page<Activity> getSiteFeed(String siteId, Pageable pageable) {
        return activityRepository.findBySiteIdOrderByPostedAtDesc(siteId, pageable);
    }

    /**
     * Get global activity feed (all users).
     */
    @Transactional(readOnly = true)
    public Page<Activity> getGlobalFeed(Pageable pageable) {
        return activityRepository.findAllByOrderByPostedAtDesc(pageable);
    }

    /**
     * Get activities for a specific node.
     */
    @Transactional(readOnly = true)
    public Page<Activity> getNodeFeed(UUID nodeId, Pageable pageable) {
        return activityRepository.findByNodeIdOrderByPostedAtDesc(nodeId, pageable);
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
}
