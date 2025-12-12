package com.ecm.core.event;

import com.ecm.core.entity.Node;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NodeMovedEvent extends ApplicationEvent {
    private final Node node;
    private final Node oldParent;
    private final Node newParent;
    private final String username;

    public NodeMovedEvent(Node node, Node oldParent, Node newParent, String username) {
        super(node);
        this.node = node;
        this.oldParent = oldParent;
        this.newParent = newParent;
        this.username = username;
    }
}
