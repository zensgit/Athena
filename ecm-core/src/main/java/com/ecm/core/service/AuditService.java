package com.ecm.core.service;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import com.ecm.core.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String eventType, UUID nodeId, String nodeName, String username, String details) {
        try {
            AuditLog logEntry = AuditLog.builder()
                .eventType(eventType)
                .nodeId(nodeId)
                .nodeName(nodeName)
                .username(username)
                .eventTime(LocalDateTime.now())
                .details(details)
                .build();
            
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }
    
    public void logNodeCreated(Node node, String username) {
        logEvent("NODE_CREATED", node.getId(), node.getName(), username, 
            String.format("Created %s: %s", node.getNodeType(), node.getName()));
    }
    
    public void logNodeUpdated(Node node, String username) {
        logEvent("NODE_UPDATED", node.getId(), node.getName(), username, 
            String.format("Updated %s: %s", node.getNodeType(), node.getName()));
    }
    
    public void logNodeDeleted(Node node, String username, boolean permanent) {
        logEvent(permanent ? "NODE_DELETED" : "NODE_SOFT_DELETED", 
            node.getId(), node.getName(), username, 
            String.format("%s deleted %s: %s", permanent ? "Permanently" : "Soft", node.getNodeType(), node.getName()));
    }
    
    public void logNodeMoved(Node node, Node oldParent, Node newParent, String username) {
        String oldPath = oldParent != null ? oldParent.getPath() : "/";
        String newPath = newParent != null ? newParent.getPath() : "/";
        logEvent("NODE_MOVED", node.getId(), node.getName(), username, 
            String.format("Moved from %s to %s", oldPath, newPath));
    }
    
    public void logNodeCopied(Node copy, Node source, String username) {
        logEvent("NODE_COPIED", copy.getId(), copy.getName(), username, 
            String.format("Copied from %s (%s)", source.getName(), source.getId()));
    }
    
    public void logNodeLocked(Node node, String username) {
        logEvent("NODE_LOCKED", node.getId(), node.getName(), username, "Locked for editing");
    }
    
    public void logNodeUnlocked(Node node, String username) {
        logEvent("NODE_UNLOCKED", node.getId(), node.getName(), username, "Unlocked");
    }
    
    public void logVersionCreated(Version version, String username) {
        logEvent("VERSION_CREATED", version.getDocument().getId(), version.getDocument().getName(), username, 
            String.format("Created version %s", version.getVersionLabel()));
    }
    
    public void logVersionDeleted(Version version, String username) {
        logEvent("VERSION_DELETED", version.getDocument().getId(), version.getDocument().getName(), username, 
            String.format("Deleted version %s", version.getVersionLabel()));
    }
    
    public void logVersionReverted(Node document, Version targetVersion, String username) {
        logEvent("VERSION_REVERTED", document.getId(), document.getName(), username, 
            String.format("Reverted to version %s", targetVersion.getVersionLabel()));
    }
}