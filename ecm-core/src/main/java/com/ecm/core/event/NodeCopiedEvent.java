package com.ecm.core.event;

import com.ecm.core.entity.Node;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NodeCopiedEvent extends ApplicationEvent {
    private final Node node;
    private final Node sourceNode;
    private final String username;

    public NodeCopiedEvent(Node node, Node sourceNode, String username) {
        super(node);
        this.node = node;
        this.sourceNode = sourceNode;
        this.username = username;
    }
}
