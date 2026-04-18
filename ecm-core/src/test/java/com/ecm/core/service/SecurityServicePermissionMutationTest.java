package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.Permission.AuthorityType;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.PermissionSet;
import com.ecm.core.event.NodePermissionsChangedEvent;
import com.ecm.core.repository.GroupRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import com.ecm.core.repository.RoleRepository;
import com.ecm.core.repository.UserRepository;
import com.ecm.core.security.DynamicAuthority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServicePermissionMutationTest {

    @Mock private UserRepository userRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SecurityService securityService;

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(
            userRepository,
            groupRepository,
            roleRepository,
            permissionRepository,
            nodeRepository,
            List.<DynamicAuthority>of(),
            eventPublisher
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("setPermission publishes subtree refresh event when ACL changes")
    void setPermissionPublishesEvent() {
        Document node = document("contracts", "owner");
        authenticate("owner");

        when(permissionRepository.findByNodeIdAndAuthority(node.getId(), "alice")).thenReturn(List.of());

        securityService.setPermission(node, "alice", AuthorityType.USER, PermissionType.READ, true);

        ArgumentCaptor<NodePermissionsChangedEvent> eventCaptor =
            ArgumentCaptor.forClass(NodePermissionsChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(node.getId(), eventCaptor.getValue().getNode().getId());
        assertTrue(eventCaptor.getValue().isIncludeDescendants());
        assertEquals("owner", eventCaptor.getValue().getUsername());
    }

    @Test
    @DisplayName("applyPermissionSet publishes a single refresh after batched ACL updates")
    void applyPermissionSetPublishesSingleRefresh() {
        Document node = document("contracts", "owner");
        authenticate("owner");

        Permission stale = new Permission();
        stale.setNode(node);
        stale.setAuthority("alice");
        stale.setAuthorityType(AuthorityType.USER);
        stale.setPermission(PermissionType.DELETE);
        stale.setAllowed(true);

        when(permissionRepository.findByNodeIdAndAuthority(node.getId(), "alice"))
            .thenReturn(List.of(stale), List.of(stale), List.of(stale));

        securityService.applyPermissionSet(node, "alice", AuthorityType.USER, PermissionSet.CONSUMER, true);

        verify(permissionRepository).delete(stale);
        verify(eventPublisher, times(1)).publishEvent(any(NodePermissionsChangedEvent.class));
    }

    @Test
    @DisplayName("setInheritPermissions no-ops without emitting refresh when value is unchanged")
    void setInheritPermissionsNoOpSkipsEvent() {
        Document node = document("contracts", "owner");
        node.setInheritPermissions(true);
        authenticate("owner");

        securityService.setInheritPermissions(node, true);

        verify(nodeRepository, never()).save(node);
        verify(eventPublisher, never()).publishEvent(any(NodePermissionsChangedEvent.class));
    }

    @Test
    @DisplayName("cleanupExpiredPermissions publishes refresh for affected nodes")
    void cleanupExpiredPermissionsPublishesRefreshForAffectedNodes() {
        Document node = document("contracts", "owner");
        authenticate("owner");

        Permission expired = new Permission();
        expired.setNode(node);
        expired.setAuthority("alice");
        expired.setAuthorityType(AuthorityType.USER);
        expired.setPermission(PermissionType.READ);
        expired.setAllowed(true);

        when(permissionRepository.findExpiredPermissions()).thenReturn(List.of(expired));
        when(nodeRepository.findAllById(anyIterable())).thenReturn(List.of(node));

        securityService.cleanupExpiredPermissions();

        verify(permissionRepository).deleteAll(List.of(expired));
        verify(eventPublisher).publishEvent(any(NodePermissionsChangedEvent.class));
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(username, "n/a", Collections.emptyList())
        );
    }

    private Document document(String name, String createdBy) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setCreatedBy(createdBy);
        document.setInheritPermissions(true);
        return document;
    }
}
