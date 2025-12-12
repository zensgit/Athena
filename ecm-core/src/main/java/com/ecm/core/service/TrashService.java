package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Trash Service
 *
 * Manages soft-deleted documents in a recycle bin.
 * Provides restore and permanent delete capabilities.
 *
 * Inspired by Alfresco's Archive Store functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrashService {

    private final NodeRepository nodeRepository;
    private final SecurityService securityService;

    @Value("${ecm.trash.retention-days:30}")
    private int retentionDays;

    @Value("${ecm.trash.auto-purge-enabled:true}")
    private boolean autoPurgeEnabled;

    /**
     * Move a node to trash (soft delete)
     */
    @Transactional
    public void moveToTrash(UUID nodeId) {
        Node node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));

        // Check delete permission
        if (!securityService.hasPermission(node, PermissionType.DELETE)) {
            throw new SecurityException("No permission to delete this document");
        }

        String currentUser = securityService.getCurrentUser();

        // Soft delete
        node.setDeleted(true);
        node.setDeletedAt(LocalDateTime.now());
        node.setDeletedBy(currentUser);

        // Also soft delete children if it's a folder
        if (node.isFolder()) {
            softDeleteChildren(node, currentUser);
        }

        nodeRepository.save(node);
        log.info("Node {} moved to trash by {}", nodeId, currentUser);
    }

    /**
     * Restore a node from trash
     */
    @Transactional
    public void restore(UUID nodeId) {
        Node node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));

        if (!node.isDeleted()) {
            throw new IllegalStateException("Node is not in trash: " + nodeId);
        }

        // Check if user can restore (must be original deleter or admin)
        String currentUser = securityService.getCurrentUser();
        boolean isDeleter = currentUser.equals(node.getDeletedBy());
        boolean isAdmin = securityService.isAdmin(currentUser);
        boolean isOwner = currentUser.equals(node.getCreatedBy());

        if (!isDeleter && !isAdmin && !isOwner) {
            throw new SecurityException("No permission to restore this document");
        }

        // Check if parent still exists and is not deleted
        if (node.getParent() != null && node.getParent().isDeleted()) {
            throw new IllegalStateException("Cannot restore: parent folder is also in trash. Restore parent first.");
        }

        // Restore
        node.setDeleted(false);
        node.setDeletedAt(null);
        node.setDeletedBy(null);

        // Also restore children if it's a folder
        if (node.isFolder()) {
            restoreChildren(node);
        }

        nodeRepository.save(node);
        log.info("Node {} restored from trash by {}", nodeId, currentUser);
    }

    /**
     * Permanently delete a node (cannot be undone)
     */
    @Transactional
    public void permanentDelete(UUID nodeId) {
        Node node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));

        if (!node.isDeleted()) {
            throw new IllegalStateException("Node must be in trash before permanent deletion. Move to trash first.");
        }

        // Check if user can permanently delete (admin only or original owner)
        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.isAdmin(currentUser);
        boolean isOwner = currentUser.equals(node.getCreatedBy());

        if (!isAdmin && !isOwner) {
            throw new SecurityException("No permission to permanently delete. Admin or owner access required.");
        }

        // Delete children first if it's a folder
        if (node.isFolder()) {
            permanentDeleteChildren(node);
        }

        nodeRepository.delete(node);
        log.info("Node {} permanently deleted by {}", nodeId, currentUser);
    }

    /**
     * Get all items in trash for current user
     */
    public List<Node> getTrashItems() {
        String currentUser = securityService.getCurrentUser();

        if (securityService.isAdmin(currentUser)) {
            // Admin sees all trash items
            return nodeRepository.findDeletedNodes();
        }

        // Regular user sees only their deleted items
        return nodeRepository.findDeletedByUser(currentUser);
    }

    /**
     * Get trash items for a specific user
     */
    public List<Node> getTrashItemsForUser(String username) {
        // Only admin can view other users' trash
        String currentUser = securityService.getCurrentUser();
        if (!securityService.isAdmin(currentUser) && !currentUser.equals(username)) {
            throw new SecurityException("No permission to view other users' trash");
        }

        return nodeRepository.findDeletedByUser(username);
    }

    /**
     * Empty trash for current user
     */
    @Transactional
    public int emptyTrash() {
        String currentUser = securityService.getCurrentUser();
        List<Node> trashItems;

        if (securityService.isAdmin(currentUser)) {
            // Admin can empty all trash
            trashItems = nodeRepository.findDeletedNodes();
        } else {
            // Regular user can only empty their own trash
            trashItems = nodeRepository.findDeletedByUser(currentUser);
        }

        int count = 0;
        for (Node node : trashItems) {
            // Skip children (they will be deleted with parent)
            if (node.getParent() != null && node.getParent().isDeleted()) {
                continue;
            }
            permanentDeleteChildren(node);
            nodeRepository.delete(node);
            count++;
        }

        log.info("Trash emptied by {}: {} items permanently deleted", currentUser, count);
        return count;
    }

    /**
     * Get trash statistics
     */
    public TrashStats getTrashStats() {
        String currentUser = securityService.getCurrentUser();
        List<Node> trashItems;

        if (securityService.isAdmin(currentUser)) {
            trashItems = nodeRepository.findDeletedNodes();
        } else {
            trashItems = nodeRepository.findDeletedByUser(currentUser);
        }

        long totalSize = 0;
        int fileCount = 0;
        int folderCount = 0;

        for (Node node : trashItems) {
            if (node.isFolder()) {
                folderCount++;
            } else {
                fileCount++;
                if (node.getSize() != null) {
                    totalSize += node.getSize();
                }
            }
        }

        LocalDateTime oldestItem = trashItems.stream()
            .map(Node::getDeletedAt)
            .min(LocalDateTime::compareTo)
            .orElse(null);

        return new TrashStats(fileCount, folderCount, totalSize, oldestItem);
    }

    /**
     * Scheduled task to purge old trash items
     */
    @Scheduled(cron = "0 0 2 * * *") // Run daily at 2 AM
    @Transactional
    public void purgeOldTrashItems() {
        if (!autoPurgeEnabled) {
            log.debug("Auto-purge is disabled");
            return;
        }

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<Node> oldItems = nodeRepository.findDeletedBefore(cutoffDate);

        int count = 0;
        for (Node node : oldItems) {
            // Skip children (they will be deleted with parent)
            if (node.getParent() != null && node.getParent().isDeleted()) {
                continue;
            }
            try {
                permanentDeleteChildren(node);
                nodeRepository.delete(node);
                count++;
            } catch (Exception e) {
                log.error("Failed to purge trash item {}: {}", node.getId(), e.getMessage());
            }
        }

        if (count > 0) {
            log.info("Auto-purge completed: {} items older than {} days permanently deleted",
                count, retentionDays);
        }
    }

    /**
     * Get items that will be auto-purged soon
     */
    public List<Node> getItemsNearingPurge(int daysBeforePurge) {
        LocalDateTime warningDate = LocalDateTime.now().minusDays(retentionDays - daysBeforePurge);
        return nodeRepository.findDeletedBefore(warningDate);
    }

    // Helper methods

    private void softDeleteChildren(Node parent, String deletedBy) {
        List<Node> children = nodeRepository.findByParentId(parent.getId());
        for (Node child : children) {
            child.setDeleted(true);
            child.setDeletedAt(LocalDateTime.now());
            child.setDeletedBy(deletedBy);
            nodeRepository.save(child);

            if (child.isFolder()) {
                softDeleteChildren(child, deletedBy);
            }
        }
    }

    private void restoreChildren(Node parent) {
        List<Node> children = nodeRepository.findDeletedChildren(parent.getId());
        for (Node child : children) {
            child.setDeleted(false);
            child.setDeletedAt(null);
            child.setDeletedBy(null);
            nodeRepository.save(child);

            if (child.isFolder()) {
                restoreChildren(child);
            }
        }
    }

    private void permanentDeleteChildren(Node parent) {
        List<Node> children = nodeRepository.findByParentId(parent.getId());
        for (Node child : children) {
            if (child.isFolder()) {
                permanentDeleteChildren(child);
            }
            nodeRepository.delete(child);
        }
    }

    // Stats record
    public record TrashStats(
        int fileCount,
        int folderCount,
        long totalSizeBytes,
        LocalDateTime oldestItemDate
    ) {
        public String formattedSize() {
            if (totalSizeBytes < 1024) return totalSizeBytes + " B";
            if (totalSizeBytes < 1024 * 1024) return String.format("%.1f KB", totalSizeBytes / 1024.0);
            if (totalSizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024));
            return String.format("%.1f GB", totalSizeBytes / (1024.0 * 1024 * 1024));
        }
    }
}
