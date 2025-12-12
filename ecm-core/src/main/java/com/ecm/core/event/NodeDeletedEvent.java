package com.ecm.core.event;

import com.ecm.core.entity.Node;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NodeDeletedEvent extends ApplicationEvent {
    private final Node node;
    private final String username;
    private final boolean permanent;

    public NodeDeletedEvent(Node node, String username, boolean permanent) {
        super(node);
        this.node = node;
        this.username = username;
        this.permanent = permanent;
    }
}
