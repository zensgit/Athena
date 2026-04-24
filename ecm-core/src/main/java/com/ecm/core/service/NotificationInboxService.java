package com.ecm.core.service;

import com.ecm.core.entity.Activity;
import com.ecm.core.entity.Notification;
import com.ecm.core.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Notification inbox service — routes activities to follower inboxes
 * and provides read/unread management for the current user.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationInboxService {

    private final NotificationRepository notificationRepository;
    private final FollowingService followingService;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    // ------------------------------------------------------------------ routing

    /**
     * Route an activity to the inboxes of all users who follow the activity's
     * user, site, or node. Called after an activity is persisted.
     */
    @Transactional
    public void routeActivityToFollowers(Activity activity) {
        if (!tenantWorkspaceScopeService.isActivityVisible(activity)) {
            log.debug("Skipping notification routing for out-of-scope activity {}", activity != null ? activity.getId() : null);
            return;
        }
        Set<String> recipients = resolveRecipients(activity);
        // don't notify the actor about their own action
        recipients.remove(activity.getUserId());

        if (recipients.isEmpty()) return;

        for (String userId : recipients) {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setActivity(activity);
            notificationRepository.save(n);
        }
        log.debug("Routed activity {} to {} recipients", activity.getId(), recipients.size());
    }

    /**
     * Route an activity directly to a single user's inbox without going through
     * follower resolution. Used for owner-scoped operational alerts such as
     * failed scheduled deliveries.
     */
    @Transactional
    public Notification createDirectNotification(String userId, Activity activity) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Notification user id is required");
        }
        if (activity == null) {
            throw new IllegalArgumentException("Notification activity is required");
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setActivity(activity);
        Notification saved = notificationRepository.save(notification);
        log.debug("Created direct notification {} for user {} from activity {}", saved.getId(), userId, activity.getId());
        return saved;
    }

    // ------------------------------------------------------------------ inbox read

    @Transactional(readOnly = true)
    public Page<Notification> getInbox(Pageable pageable) {
        String userId = securityService.getCurrentUser();
        return filterVisibleNotifications(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged()), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getUnread(Pageable pageable) {
        String userId = securityService.getCurrentUser();
        return filterVisibleNotifications(notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false, Pageable.unpaged()), pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        String userId = securityService.getCurrentUser();
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return notificationRepository.countByUserIdAndReadFalse(userId);
        }
        return notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false, Pageable.unpaged())
            .getContent().stream()
            .filter(this::isVisible)
            .count();
    }

    // ------------------------------------------------------------------ inbox write

    @Transactional
    public Notification markRead(UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));
        checkOwner(n);
        assertVisible(n);
        n.setRead(true);
        n.setReadAt(LocalDateTime.now());
        return notificationRepository.save(n);
    }

    @Transactional
    public int markAllRead() {
        String userId = securityService.getCurrentUser();
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return notificationRepository.markAllRead(userId, LocalDateTime.now());
        }

        LocalDateTime now = LocalDateTime.now();
        List<Notification> unread = notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false, Pageable.unpaged())
            .getContent().stream()
            .filter(this::isVisible)
            .toList();
        unread.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(now);
            notificationRepository.save(notification);
        });
        return unread.size();
    }

    @Transactional
    public void deleteNotification(UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));
        checkOwner(n);
        assertVisible(n);
        notificationRepository.delete(n);
    }

    // ------------------------------------------------------------------ cleanup

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int deleted = notificationRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} notifications older than {}", deleted, cutoff);
        }
    }

    // ------------------------------------------------------------------ internal

    private Set<String> resolveRecipients(Activity activity) {
        Set<String> recipients = new HashSet<>();

        // find all users who follow this user
        if (activity.getUserId() != null) {
            addFollowersOf("USER", activity.getUserId(), recipients);
        }
        // find all users who follow this site
        if (activity.getSiteId() != null) {
            addFollowersOf("SITE", activity.getSiteId(), recipients);
        }
        // find all users who follow this node
        if (activity.getNodeId() != null) {
            addFollowersOf("NODE", activity.getNodeId().toString(), recipients);
        }

        return recipients;
    }

    private void addFollowersOf(String targetType, String targetId, Set<String> recipients) {
        try {
            List<String> followers = followingService.getFollowersOf(targetType, targetId);
            recipients.addAll(followers);
        } catch (Exception e) {
            log.debug("Failed to resolve followers for {}:{} — {}", targetType, targetId, e.getMessage());
        }
    }

    private void checkOwner(Notification n) {
        String currentUser = securityService.getCurrentUser();
        if (!n.getUserId().equals(currentUser)) {
            throw new SecurityException("Cannot access another user's notification");
        }
    }

    private Page<Notification> filterVisibleNotifications(Page<Notification> source, Pageable pageable) {
        List<Notification> visible = source.getContent().stream()
            .filter(this::isVisible)
            .toList();
        return slice(visible, pageable);
    }

    private boolean isVisible(Notification notification) {
        return tenantWorkspaceScopeService.isActivityVisible(notification.getActivity());
    }

    private void assertVisible(Notification notification) {
        if (!isVisible(notification)) {
            throw new NoSuchElementException("Notification not found: " + notification.getId());
        }
    }

    private Page<Notification> slice(List<Notification> notifications, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(notifications);
        }

        int fromIndex = Math.toIntExact(pageable.getOffset());
        if (fromIndex >= notifications.size()) {
            return new PageImpl<>(List.of(), pageable, notifications.size());
        }
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), notifications.size());
        return new PageImpl<>(notifications.subList(fromIndex, toIndex), pageable, notifications.size());
    }
}
