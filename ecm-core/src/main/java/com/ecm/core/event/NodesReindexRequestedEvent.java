package com.ecm.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
public class NodesReindexRequestedEvent extends ApplicationEvent {
    private final List<UUID> nodeIds;
    private final String username;

    public NodesReindexRequestedEvent(Collection<UUID> nodeIds, String username) {
        super(nodeIds == null ? List.of() : List.copyOf(nodeIds));
        this.nodeIds = nodeIds == null
            ? List.of()
            : nodeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        this.username = username;
    }
}
