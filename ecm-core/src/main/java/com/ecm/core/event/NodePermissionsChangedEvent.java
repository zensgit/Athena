package com.ecm.core.event;

import com.ecm.core.entity.Node;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NodePermissionsChangedEvent extends ApplicationEvent {
    private final Node node;
    private final String username;
    private final boolean includeDescendants;

    public NodePermissionsChangedEvent(Node node, String username, boolean includeDescendants) {
        super(node);
        this.node = node;
        this.username = username;
        this.includeDescendants = includeDescendants;
    }
}
