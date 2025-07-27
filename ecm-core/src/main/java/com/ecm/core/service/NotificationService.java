package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final RabbitTemplate rabbitTemplate;
    
    public void notifyNodeCreated(Node node) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NODE_CREATED");
        notification.put("nodeId", node.getId());
        notification.put("nodeName", node.getName());
        notification.put("nodeType", node.getNodeType());
        notification.put("createdBy", node.getCreatedBy());
        notification.put("timestamp", System.currentTimeMillis());
        
        sendNotification(notification);
    }
    
    public void notifyNodeDeleted(Node node) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NODE_DELETED");
        notification.put("nodeId", node.getId());
        notification.put("nodeName", node.getName());
        notification.put("nodeType", node.getNodeType());
        notification.put("deletedBy", node.getLastModifiedBy());
        notification.put("timestamp", System.currentTimeMillis());
        
        sendNotification(notification);
    }
    
    public void notifyNodeLocked(Node node, String lockedBy) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NODE_LOCKED");
        notification.put("nodeId", node.getId());
        notification.put("nodeName", node.getName());
        notification.put("lockedBy", lockedBy);
        notification.put("timestamp", System.currentTimeMillis());
        
        sendNotification(notification);
    }
    
    public void notifyNodeUnlocked(Node node, String unlockedBy) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NODE_UNLOCKED");
        notification.put("nodeId", node.getId());
        notification.put("nodeName", node.getName());
        notification.put("unlockedBy", unlockedBy);
        notification.put("timestamp", System.currentTimeMillis());
        
        sendNotification(notification);
    }
    
    public void notifyVersionCreated(Version version) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "VERSION_CREATED");
        notification.put("documentId", version.getDocument().getId());
        notification.put("documentName", version.getDocument().getName());
        notification.put("versionLabel", version.getVersionLabel());
        notification.put("createdBy", version.getCreatedBy());
        notification.put("timestamp", System.currentTimeMillis());
        
        sendNotification(notification);
    }
    
    public void notifyVersionReverted(Node document, Version targetVersion) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "VERSION_REVERTED");
        notification.put("documentId", document.getId());
        notification.put("documentName", document.getName());
        notification.put("targetVersion", targetVersion.getVersionLabel());
        notification.put("revertedBy", document.getLastModifiedBy());
        notification.put("timestamp", System.currentTimeMillis());
        
        sendNotification(notification);
    }
    
    private void sendNotification(Map<String, Object> notification) {
        try {
            // Send to RabbitMQ
            rabbitTemplate.convertAndSend("ecm.notifications", notification);
            log.debug("Sent notification: {}", notification);
        } catch (Exception e) {
            log.error("Failed to send notification", e);
        }
    }
}