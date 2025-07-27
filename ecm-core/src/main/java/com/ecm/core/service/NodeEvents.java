package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
abstract class BaseNodeEvent extends ApplicationEvent {
    private final Node node;
    private final String username;
    private final LocalDateTime timestamp;
    
    public BaseNodeEvent(Node node) {
        super(node);
        this.node = node;
        this.username = node.getLastModifiedBy();
        this.timestamp = LocalDateTime.now();
    }
}

@Getter
class NodeCreatedEvent extends BaseNodeEvent {
    public NodeCreatedEvent(Node node) {
        super(node);
    }
}

@Getter
class NodeUpdatedEvent extends BaseNodeEvent {
    public NodeUpdatedEvent(Node node) {
        super(node);
    }
}

@Getter
class NodeDeletedEvent extends BaseNodeEvent {
    private final boolean permanent;
    
    public NodeDeletedEvent(Node node, boolean permanent) {
        super(node);
        this.permanent = permanent;
    }
}

@Getter
class NodeMovedEvent extends BaseNodeEvent {
    private final Node oldParent;
    private final Node newParent;
    
    public NodeMovedEvent(Node node, Node oldParent, Node newParent) {
        super(node);
        this.oldParent = oldParent;
        this.newParent = newParent;
    }
}

@Getter
class NodeCopiedEvent extends BaseNodeEvent {
    private final Node sourceNode;
    
    public NodeCopiedEvent(Node copiedNode, Node sourceNode) {
        super(copiedNode);
        this.sourceNode = sourceNode;
    }
}

@Getter
class NodeLockedEvent extends BaseNodeEvent {
    public NodeLockedEvent(Node node) {
        super(node);
    }
}

@Getter
class NodeUnlockedEvent extends BaseNodeEvent {
    public NodeUnlockedEvent(Node node) {
        super(node);
    }
}

@Getter
class VersionCreatedEvent extends ApplicationEvent {
    private final Version version;
    private final String username;
    private final LocalDateTime timestamp;
    
    public VersionCreatedEvent(Version version) {
        super(version);
        this.version = version;
        this.username = version.getCreatedBy();
        this.timestamp = LocalDateTime.now();
    }
}

@Getter
class VersionDeletedEvent extends ApplicationEvent {
    private final Version version;
    private final String username;
    private final LocalDateTime timestamp;
    
    public VersionDeletedEvent(Version version) {
        super(version);
        this.version = version;
        this.username = version.getLastModifiedBy();
        this.timestamp = LocalDateTime.now();
    }
}

@Getter
class VersionRevertedEvent extends ApplicationEvent {
    private final Node document;
    private final Version targetVersion;
    private final String username;
    private final LocalDateTime timestamp;
    
    public VersionRevertedEvent(Node document, Version targetVersion) {
        super(document);
        this.document = document;
        this.targetVersion = targetVersion;
        this.username = document.getLastModifiedBy();
        this.timestamp = LocalDateTime.now();
    }
}

@Getter
class VersionPromotedEvent extends ApplicationEvent {
    private final Version version;
    private final String username;
    private final LocalDateTime timestamp;
    
    public VersionPromotedEvent(Version version) {
        super(version);
        this.version = version;
        this.username = version.getLastModifiedBy();
        this.timestamp = LocalDateTime.now();
    }
}