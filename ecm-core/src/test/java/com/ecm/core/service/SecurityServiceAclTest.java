package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.Permission.AuthorityType;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.Role;
import com.ecm.core.entity.User;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServiceAclTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private NodeRepository nodeRepository;

    private SecurityService securityService;

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(
            userRepository,
            groupRepository,
            roleRepository,
            permissionRepository,
            nodeRepository,
            List.<DynamicAuthority>of()
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Child-level deny overrides inherited allow")
    void childDenyOverridesInheritedAllow() {
        Document parent = document("parent", "admin");
        Document child = document("child", "admin");
        child.setParent(parent);
        child.setInheritPermissions(true);

        Permission childDeny = permission(child, "EVERYONE", PermissionType.READ, false);

        when(permissionRepository.findByNodeId(child.getId()))
            .thenReturn(List.of(childDeny));
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.empty());

        boolean allowed = securityService.hasPermission(child, PermissionType.READ, "viewer");

        assertFalse(allowed);
    }

    @Test
    @DisplayName("Disabled inheritance ignores parent permissions")
    void inheritanceDisabledIgnoresParentPermissions() {
        Document parent = document("parent", "admin");
        Document child = document("child", "admin");
        child.setParent(parent);
        child.setInheritPermissions(false);

        when(permissionRepository.findByNodeId(child.getId()))
            .thenReturn(List.of());
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.empty());

        boolean allowed = securityService.hasPermission(child, PermissionType.READ, "viewer");

        assertFalse(allowed);
    }

    @Test
    @DisplayName("Role-based allow is honored via user repository")
    void roleBasedAllowUsesUserRepository() {
        Document parent = document("parent", "admin");
        Document child = document("child", "admin");
        child.setParent(parent);
        child.setInheritPermissions(true);

        Permission parentAllow = permission(parent, "ROLE_VIEWER", PermissionType.READ, true);

        when(permissionRepository.findByNodeId(child.getId()))
            .thenReturn(List.of());
        when(permissionRepository.findByNodeId(parent.getId()))
            .thenReturn(List.of(parentAllow));

        Role viewerRole = new Role();
        viewerRole.setName("ROLE_VIEWER");

        User viewer = new User();
        viewer.setUsername("viewer");
        viewer.setEmail("viewer@example.com");
        viewer.setPassword("password");
        viewer.setRoles(Set.of(viewerRole));

        when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewer));

        boolean allowed = securityService.hasPermission(child, PermissionType.READ, "viewer");

        assertTrue(allowed);
    }

    private static Document document(String name, String createdBy) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setCreatedBy(createdBy);
        document.setInheritPermissions(true);
        return document;
    }

    private static Permission permission(Document node, String authority, PermissionType type, boolean allowed) {
        Permission permission = new Permission();
        permission.setNode(node);
        permission.setAuthority(authority);
        permission.setAuthorityType(resolveAuthorityType(authority));
        permission.setPermission(type);
        permission.setAllowed(allowed);
        return permission;
    }

    private static AuthorityType resolveAuthorityType(String authority) {
        if ("EVERYONE".equalsIgnoreCase(authority)) {
            return AuthorityType.EVERYONE;
        }
        if (authority != null && authority.startsWith("ROLE_")) {
            return AuthorityType.ROLE;
        }
        return AuthorityType.USER;
    }
}
