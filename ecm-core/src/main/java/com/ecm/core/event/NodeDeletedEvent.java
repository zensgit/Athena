package com.ecm.core.event;

import com.ecm.core.entity.Node;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
public class NodeDeletedEvent extends ApplicationEvent {
    private final Node node;
    private final String username;
    private final boolean permanent;
    private final String nodePath;
    private final Set<String> readableAuthorities;

    public NodeDeletedEvent(Node node, String username, boolean permanent) {
        this(node, username, permanent, node != null ? node.getPath() : null, Collections.emptySet());
    }

    public NodeDeletedEvent(Node node, String username, boolean permanent, String nodePath, Set<String> readableAuthorities) {
        super(node);
        this.node = node;
        this.username = username;
        this.permanent = permanent;
        this.nodePath = nodePath;
        this.readableAuthorities = readableAuthorities != null
            ? Collections.unmodifiableSet(new LinkedHashSet<>(readableAuthorities))
            : Collections.emptySet();
    }
}
