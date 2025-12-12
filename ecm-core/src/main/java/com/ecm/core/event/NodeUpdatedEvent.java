package com.ecm.core.event;

import com.ecm.core.entity.Node;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NodeUpdatedEvent extends ApplicationEvent {
    private final Node node;
    private final String username;

    public NodeUpdatedEvent(Node node, String username) {
        super(node);
        this.node = node;
        this.username = username;
    }
}
