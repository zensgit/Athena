package com.ecm.core.event;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class VersionRevertedEvent extends ApplicationEvent {
    private final Node document;
    private final Version targetVersion;
    private final String username;

    public VersionRevertedEvent(Node document, Version targetVersion, String username) {
        super(document);
        this.document = document;
        this.targetVersion = targetVersion;
        this.username = username;
    }
}
