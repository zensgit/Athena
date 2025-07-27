package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    
    private final JdbcTemplate jdbcTemplate;
    
    public void logNodeCreated(Node node, String username) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql, 
            UUID.randomUUID(),
            "NODE_CREATED",
            node.getId(),
            node.getName(),
            username,
            LocalDateTime.now(),
            String.format("Created %s: %s", node.getNodeType(), node.getName())
        );
    }
    
    public void logNodeUpdated(Node node, String username) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
            UUID.randomUUID(),
            "NODE_UPDATED",
            node.getId(),
            node.getName(),
            username,
            LocalDateTime.now(),
            String.format("Updated %s: %s", node.getNodeType(), node.getName())
        );
    }
    
    public void logNodeDeleted(Node node, String username, boolean permanent) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
            UUID.randomUUID(),
            permanent ? "NODE_DELETED" : "NODE_SOFT_DELETED",
            node.getId(),
            node.getName(),
            username,
            LocalDateTime.now(),
            String.format("%s deleted %s: %s", 
                permanent ? "Permanently" : "Soft", node.getNodeType(), node.getName())
        );
    }
    
    public void logNodeMoved(Node node, Node oldParent, Node newParent, String username) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        String oldPath = oldParent != null ? oldParent.getPath() : "/";
        String newPath = newParent != null ? newParent.getPath() : "/";
        
        jdbcTemplate.update(sql,
            UUID.randomUUID(),
            "NODE_MOVED",
            node.getId(),
            node.getName(),
            username,
            LocalDateTime.now(),
            String.format("Moved from %s to %s", oldPath, newPath)
        );
    }
    
    public void logNodeCopied(Node copy, Node source, String username) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
            UUID.randomUUID(),
            "NODE_COPIED",
            copy.getId(),
            copy.getName(),
            username,
            LocalDateTime.now(),
            String.format("Copied from %s (%s)", source.getName(), source.getId())
        );
    }
    
    public void logNodeLocked(Node node, String username) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
            UUID.randomUUID(),
            "NODE_LOCKED",
            node.getId(),
            node.getName(),
            username,
            LocalDateTime.now(),
            "Locked for editing"
        );
    }
    
    public void logNodeUnlocked(Node node, String username) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
            UUID.randomUUID(),
            "NODE_UNLOCKED",
            node.getId(),
            node.getName(),
            username,
            LocalDateTime.now(),
            "Unlocked"
        );
    }
    
    public void logVersionCreated(Version version, String username) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
            UUID.randomUUID(),
            "VERSION_CREATED",
            version.getDocument().getId(),
            version.getDocument().getName(),
            username,
            LocalDateTime.now(),
            String.format("Created version %s", version.getVersionLabel())
        );
    }
    
    public void logVersionDeleted(Version version, String username) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
            UUID.randomUUID(),
            "VERSION_DELETED",
            version.getDocument().getId(),
            version.getDocument().getName(),
            username,
            LocalDateTime.now(),
            String.format("Deleted version %s", version.getVersionLabel())
        );
    }
    
    public void logVersionReverted(Node document, Version targetVersion, String username) {
        String sql = "INSERT INTO audit_log (id, event_type, node_id, node_name, username, event_time, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
            UUID.randomUUID(),
            "VERSION_REVERTED",
            document.getId(),
            document.getName(),
            username,
            LocalDateTime.now(),
            String.format("Reverted to version %s", targetVersion.getVersionLabel())
        );
    }
}