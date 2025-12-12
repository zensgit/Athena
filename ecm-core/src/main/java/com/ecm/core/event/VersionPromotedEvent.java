package com.ecm.core.event;

import com.ecm.core.entity.Version;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class VersionPromotedEvent extends ApplicationEvent {
    private final Version version;
    private final String username;

    public VersionPromotedEvent(Version version, String username) {
        super(version);
        this.version = version;
        this.username = username;
    }

    public VersionPromotedEvent(Version version) {
        super(version);
        this.version = version;
        this.username = null;
    }
}
