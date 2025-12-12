package com.ecm.core.event;

import com.ecm.core.entity.Version;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class VersionCreatedEvent extends ApplicationEvent {
    private final Version version;
    private final String username;

    public VersionCreatedEvent(Version version, String username) {
        super(version);
        this.version = version;
        this.username = username;
    }
}
