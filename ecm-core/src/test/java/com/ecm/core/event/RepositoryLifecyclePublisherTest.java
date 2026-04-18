package com.ecm.core.event;

import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RepositoryLifecyclePublisherTest {

    @Test
    @DisplayName("publishNodeUpdated emits legacy update event and lifecycle event")
    void publishNodeUpdatedEmitsLegacyAndLifecycleEvents() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("report.docx");

        RepositoryLifecyclePublisher.publishNodeUpdated(
            publisher,
            document,
            "alice",
            TriggerType.DOCUMENT_UPDATED
        );

        verify(publisher).publishEvent(any(NodeUpdatedEvent.class));
        org.mockito.ArgumentCaptor<RepositoryLifecycleEvent> lifecycleCaptor =
            org.mockito.ArgumentCaptor.forClass(RepositoryLifecycleEvent.class);
        verify(publisher).publishEvent(lifecycleCaptor.capture());
        RepositoryLifecycleEvent lifecycleEvent = lifecycleCaptor.getValue();
        assertEquals(RepositoryLifecycleAction.NODE_UPDATED, lifecycleEvent.getAction());
        assertEquals(document.getId(), lifecycleEvent.getDocument().getId());
        assertEquals("alice", lifecycleEvent.getUsername());
        assertEquals(TriggerType.DOCUMENT_UPDATED, lifecycleEvent.getRuleTriggerType());
    }

    @Test
    @DisplayName("publishNodePermissionsChanged emits legacy permissions event and lifecycle event")
    void publishNodePermissionsChangedEmitsLegacyAndLifecycleEvents() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("contracts");

        RepositoryLifecyclePublisher.publishNodePermissionsChanged(
            publisher,
            document,
            "owner",
            true
        );

        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.any(NodePermissionsChangedEvent.class));
        org.mockito.ArgumentCaptor<RepositoryLifecycleEvent> lifecycleCaptor =
            org.mockito.ArgumentCaptor.forClass(RepositoryLifecycleEvent.class);
        verify(publisher).publishEvent(lifecycleCaptor.capture());
        RepositoryLifecycleEvent lifecycleEvent = lifecycleCaptor.getValue();
        assertEquals(RepositoryLifecycleAction.NODE_PERMISSIONS_CHANGED, lifecycleEvent.getAction());
        assertEquals(document.getId(), lifecycleEvent.getDocument().getId());
        assertEquals("owner", lifecycleEvent.getUsername());
        assertEquals(true, lifecycleEvent.isIncludeDescendants());
        assertNull(lifecycleEvent.getRuleTriggerType());
    }

    @Test
    @DisplayName("publishVersionCreated emits legacy version event and lifecycle event")
    void publishVersionCreatedEmitsLegacyAndLifecycleEvents() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("report.docx");

        Version version = new Version();
        version.setId(UUID.randomUUID());
        version.setDocument(document);
        version.setVersionLabel("1.1");

        RepositoryLifecyclePublisher.publishVersionCreated(
            publisher,
            version,
            "alice",
            TriggerType.VERSION_CREATED
        );

        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.any(VersionCreatedEvent.class));
        org.mockito.ArgumentCaptor<RepositoryLifecycleEvent> lifecycleCaptor =
            org.mockito.ArgumentCaptor.forClass(RepositoryLifecycleEvent.class);
        verify(publisher).publishEvent(lifecycleCaptor.capture());
        RepositoryLifecycleEvent lifecycleEvent = lifecycleCaptor.getValue();
        assertEquals(RepositoryLifecycleAction.VERSION_CREATED, lifecycleEvent.getAction());
        assertEquals(version.getId(), lifecycleEvent.getVersion().getId());
        assertEquals(document.getId(), lifecycleEvent.getDocument().getId());
        assertEquals(TriggerType.VERSION_CREATED, lifecycleEvent.getRuleTriggerType());
    }
}
