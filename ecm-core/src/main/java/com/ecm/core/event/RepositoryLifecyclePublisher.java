package com.ecm.core.event;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import com.ecm.core.entity.AutomationRule.TriggerType;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Set;

public final class RepositoryLifecyclePublisher {

    private RepositoryLifecyclePublisher() {
    }

    public static void publishNodeCreated(
        ApplicationEventPublisher eventPublisher,
        Node node,
        String username,
        TriggerType ruleTriggerType
    ) {
        eventPublisher.publishEvent(new NodeCreatedEvent(node, username));
        eventPublisher.publishEvent(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.NODE_CREATED)
            .node(node)
            .document(asDocument(node))
            .username(username)
            .ruleTriggerType(ruleTriggerType)
            .build());
    }

    public static void publishNodeUpdated(
        ApplicationEventPublisher eventPublisher,
        Node node,
        String username,
        TriggerType ruleTriggerType
    ) {
        eventPublisher.publishEvent(new NodeUpdatedEvent(node, username));
        eventPublisher.publishEvent(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.NODE_UPDATED)
            .node(node)
            .document(asDocument(node))
            .username(username)
            .ruleTriggerType(ruleTriggerType)
            .build());
    }

    public static void publishNodeMoved(
        ApplicationEventPublisher eventPublisher,
        Node node,
        Node oldParent,
        Node newParent,
        String username,
        TriggerType ruleTriggerType
    ) {
        eventPublisher.publishEvent(new NodeMovedEvent(node, oldParent, newParent, username));
        eventPublisher.publishEvent(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.NODE_MOVED)
            .node(node)
            .document(asDocument(node))
            .oldParent(oldParent)
            .newParent(newParent)
            .username(username)
            .ruleTriggerType(ruleTriggerType)
            .build());
    }

    public static void publishNodeDeleted(
        ApplicationEventPublisher eventPublisher,
        Node node,
        String username,
        boolean permanent,
        String nodePath,
        Set<String> readableAuthorities
    ) {
        eventPublisher.publishEvent(new NodeDeletedEvent(node, username, permanent, nodePath, readableAuthorities));
        eventPublisher.publishEvent(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.NODE_DELETED)
            .node(node)
            .document(asDocument(node))
            .username(username)
            .permanentDelete(permanent)
            .build());
    }

    public static void publishNodePermissionsChanged(
        ApplicationEventPublisher eventPublisher,
        Node node,
        String username,
        boolean includeDescendants
    ) {
        eventPublisher.publishEvent(new NodePermissionsChangedEvent(node, username, includeDescendants));
        eventPublisher.publishEvent(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.NODE_PERMISSIONS_CHANGED)
            .node(node)
            .document(asDocument(node))
            .username(username)
            .includeDescendants(includeDescendants)
            .build());
    }

    public static void publishNodeCheckedIn(
        ApplicationEventPublisher eventPublisher,
        Document document,
        String username
    ) {
        eventPublisher.publishEvent(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.NODE_CHECKED_IN)
            .node(document)
            .document(document)
            .username(username)
            .build());
    }

    public static void publishVersionCreated(
        ApplicationEventPublisher eventPublisher,
        Version version,
        String username,
        TriggerType ruleTriggerType
    ) {
        eventPublisher.publishEvent(new VersionCreatedEvent(version, username));
        eventPublisher.publishEvent(RepositoryLifecycleEvent.builder()
            .action(RepositoryLifecycleAction.VERSION_CREATED)
            .node(version.getDocument())
            .document(version.getDocument())
            .version(version)
            .username(username)
            .ruleTriggerType(ruleTriggerType)
            .build());
    }

    private static Document asDocument(Node node) {
        return node instanceof Document document ? document : null;
    }
}
