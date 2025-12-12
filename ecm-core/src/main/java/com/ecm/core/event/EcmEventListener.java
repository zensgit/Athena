package com.ecm.core.event;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EcmEventListener {
    
    private final AuditService auditService;
    private final SearchIndexService searchIndexService;
    private final NotificationService notificationService;
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNodeCreated(NodeCreatedEvent event) {
        log.info("Node created: {}", event.getNode().getName());
        
        // Audit
        auditService.logNodeCreated(event.getNode(), event.getUsername());
        
        // Index for search
        searchIndexService.indexNode(event.getNode());
        
        // Send notifications
        notificationService.notifyNodeCreated(event.getNode());
    }
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNodeUpdated(NodeUpdatedEvent event) {
        log.info("Node updated: {}", event.getNode().getName());
        
        // Audit
        auditService.logNodeUpdated(event.getNode(), event.getUsername());
        
        // Update search index
        searchIndexService.updateNode(event.getNode());
    }
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNodeDeleted(NodeDeletedEvent event) {
        log.info("Node deleted: {}", event.getNode().getName());
        
        // Audit
        auditService.logNodeDeleted(event.getNode(), event.getUsername(), event.isPermanent());
        
        // Remove from search index if permanent
        if (event.isPermanent()) {
            searchIndexService.deleteNode(event.getNode().getId());
        } else {
            searchIndexService.updateNode(event.getNode());
        }
        
        // Send notifications
        notificationService.notifyNodeDeleted(event.getNode());
    }
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNodeMoved(NodeMovedEvent event) {
        log.info("Node moved: {} from {} to {}", 
            event.getNode().getName(), 
            event.getOldParent() != null ? event.getOldParent().getName() : "root",
            event.getNewParent() != null ? event.getNewParent().getName() : "root");
        
        // Audit
        auditService.logNodeMoved(event.getNode(), event.getOldParent(), 
            event.getNewParent(), event.getUsername());
        
        // Update search index
        searchIndexService.updateNode(event.getNode());
        
        // Update children paths in index
        searchIndexService.updateNodeChildren(event.getNode());
    }
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNodeCopied(NodeCopiedEvent event) {
        log.info("Node copied: {} from {}", 
            event.getNode().getName(), event.getSourceNode().getName());
        
        // Audit
        auditService.logNodeCopied(event.getNode(), event.getSourceNode(), event.getUsername());
        
        // Index new node
        searchIndexService.indexNode(event.getNode());
    }
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNodeLocked(NodeLockedEvent event) {
        log.info("Node locked: {} by {}", event.getNode().getName(), event.getUsername());
        
        // Audit
        auditService.logNodeLocked(event.getNode(), event.getUsername());
        
        // Send notifications to other users
        notificationService.notifyNodeLocked(event.getNode(), event.getUsername());
    }
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNodeUnlocked(NodeUnlockedEvent event) {
        log.info("Node unlocked: {} by {}", event.getNode().getName(), event.getUsername());
        
        // Audit
        auditService.logNodeUnlocked(event.getNode(), event.getUsername());
        
        // Send notifications
        notificationService.notifyNodeUnlocked(event.getNode(), event.getUsername());
    }
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVersionCreated(VersionCreatedEvent event) {
        Version version = event.getVersion();
        Document document = version.getDocument();
        
        log.info("Version created: {} for document {}", 
            version.getVersionLabel(), document.getName());
        
        // Audit
        auditService.logVersionCreated(version, event.getUsername());
        
        // Update search index with new content
        searchIndexService.updateDocument(document);
        
        // Generate preview for new version
        try {
            // This would trigger preview generation
            log.debug("Triggering preview generation for version {}", version.getId());
        } catch (Exception e) {
            log.error("Failed to generate preview for version {}", version.getId(), e);
        }
        
        // Send notifications
        notificationService.notifyVersionCreated(version);
    }
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVersionDeleted(VersionDeletedEvent event) {
        log.info("Version deleted: {}", event.getVersion().getVersionLabel());
        
        // Audit
        auditService.logVersionDeleted(event.getVersion(), event.getUsername());
    }
    
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVersionReverted(VersionRevertedEvent event) {
        log.info("Document {} reverted to version {}", 
            event.getDocument().getName(), event.getTargetVersion().getVersionLabel());
        
        // Audit
        auditService.logVersionReverted(event.getDocument(), event.getTargetVersion(), 
            event.getUsername());
        
        // Update search index
        searchIndexService.updateDocument((Document) event.getDocument());
        
        // Send notifications
        notificationService.notifyVersionReverted(event.getDocument(), event.getTargetVersion());
    }
}
