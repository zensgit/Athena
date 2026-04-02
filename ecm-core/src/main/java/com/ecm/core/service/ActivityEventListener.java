package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Site;
import com.ecm.core.event.*;
import com.ecm.core.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens to domain events and posts activity entries with site resolution.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ActivityEventListener {

    private final ActivityService activityService;
    private final SiteRepository siteRepository;

    @Async @EventListener
    public void onNodeCreated(NodeCreatedEvent event) {
        post("node.created", event.getUsername(), event.getNode(), Map.of("action", "created"));
    }

    @Async @EventListener
    public void onNodeUpdated(NodeUpdatedEvent event) {
        post("node.updated", event.getUsername(), event.getNode(), Map.of("action", "updated"));
    }

    @Async @EventListener
    public void onNodeDeleted(NodeDeletedEvent event) {
        post("node.deleted", event.getUsername(), event.getNode(), Map.of("action", "deleted", "permanent", event.isPermanent()));
    }

    @Async @EventListener
    public void onNodeMoved(NodeMovedEvent event) {
        post("node.moved", event.getUsername(), event.getNode(), Map.of("action", "moved"));
    }

    @Async @EventListener
    public void onNodeLocked(NodeLockedEvent event) {
        post("node.locked", event.getUsername(), event.getNode(), Map.of("action", "locked"));
    }

    @Async @EventListener
    public void onNodeUnlocked(NodeUnlockedEvent event) {
        post("node.unlocked", event.getUsername(), event.getNode(), Map.of("action", "unlocked"));
    }

    @Async @EventListener
    public void onVersionCreated(VersionCreatedEvent event) {
        var doc = event.getVersion().getDocument();
        String siteId = doc != null ? resolveSiteId(doc) : null;
        activityService.postActivity(
            "version.created", event.getUsername(), siteId,
            doc != null ? doc.getId() : null,
            doc != null ? doc.getName() : null,
            Map.of("action", "version created",
                   "versionLabel", event.getVersion().getVersionLabel() != null ? event.getVersion().getVersionLabel() : "")
        );
    }

    @Async @EventListener
    public void onCommentAdded(CommentAddedEvent event) {
        try {
            var comment = event.getComment();
            String username = comment.getAuthor() != null ? comment.getAuthor() : "system";
            var node = comment.getNode();
            String siteId = node != null ? resolveSiteId(node) : null;
            activityService.postActivity(
                "comment.added", username, siteId,
                node != null ? node.getId() : null,
                node != null ? node.getName() : null,
                Map.of("action", "commented")
            );
        } catch (Exception e) {
            log.warn("Failed to post comment activity: {}", e.getMessage());
        }
    }

    // ---- site membership events (posted directly, not from Node events) -----

    /**
     * Call this from SiteMembershipService after approve/reject/create.
     */
    public void postMembershipActivity(String activityType, String userId, String siteId, Map<String, Object> summary) {
        try {
            activityService.postActivity(activityType, userId, siteId, null, null, summary);
        } catch (Exception e) {
            log.warn("Failed to post membership activity: {}", e.getMessage());
        }
    }

    public void postSiteActivity(String activityType, String userId, String siteId, Map<String, Object> summary) {
        try {
            activityService.postActivity(activityType, userId, siteId, null, null, summary);
        } catch (Exception e) {
            log.warn("Failed to post site activity: {}", e.getMessage());
        }
    }

    public void postSiteMemberActivity(
        String activityType,
        String actorUserId,
        String siteId,
        String memberUsername,
        String role
    ) {
        try {
            activityService.postActivity(
                activityType,
                actorUserId,
                siteId,
                null,
                null,
                Map.of(
                    "memberUsername", memberUsername,
                    "role", role
                )
            );
        } catch (Exception e) {
            log.warn("Failed to post site member activity: {}", e.getMessage());
        }
    }

    // ---- internal -----------------------------------------------------------

    private void post(String type, String username, Node node, Map<String, Object> summary) {
        try {
            String siteId = node != null ? resolveSiteId(node) : null;
            activityService.postActivity(
                type, username, siteId,
                node != null ? node.getId() : null,
                node != null ? node.getName() : null,
                summary
            );
        } catch (Exception e) {
            log.warn("Failed to post activity {}: {}", type, e.getMessage());
        }
    }

    /**
     * Resolve siteId by matching the node's path against site rootFolder paths.
     * Returns null if no site matches.
     */
    String resolveSiteId(Node node) {
        if (node == null || node.getPath() == null) return null;
        String nodePath = node.getPath();
        try {
            for (Site site : siteRepository.findByDeletedFalseOrderByTitleAsc()) {
                if (site.getRootFolder() != null && site.getRootFolder().getPath() != null) {
                    String rootPath = site.getRootFolder().getPath();
                    if (nodePath.startsWith(rootPath + "/") || nodePath.equals(rootPath)) {
                        return site.getSiteId();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Site resolution failed for node {}: {}", node.getId(), e.getMessage());
        }
        return null;
    }
}
