package com.ecm.core.cmis;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.Permission.AuthorityType;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.GroupRepository;
import com.ecm.core.repository.RoleRepository;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmisAclServiceTest {

    @Mock
    private SecurityService securityService;

    @Mock
    private NodeService nodeService;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private RoleRepository roleRepository;

    private CmisAclService cmisAclService;

    private Folder folder;

    @BeforeEach
    void setUp() {
        cmisAclService = new CmisAclService(securityService, nodeService, groupRepository, roleRepository);
        folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName("Contracts");
        folder.setPath("/Sites/contracts");
        folder.setCreatedBy("admin");
        folder.setCreatedDate(LocalDateTime.now());
        folder.setLastModifiedBy("admin");
        folder.setLastModifiedDate(LocalDateTime.now());
    }

    @Test
    @DisplayName("getAcl returns consolidated ACEs grouped by principal")
    void getAclReturnsConsolidatedAces() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of(
                buildPermission(folder, "alice", PermissionType.READ, false),
                buildPermission(folder, "alice", PermissionType.WRITE, false),
                buildPermission(folder, "bob", PermissionType.READ, false)
        ));

        CmisModels.AclResponse response = cmisAclService.getAcl(folder.getId().toString());

        assertEquals(folder.getId().toString(), response.objectId());
        assertTrue(response.isExact());
        assertEquals(2, response.aces().size());

        CmisModels.AceEntry aliceAce = response.aces().stream()
                .filter(a -> "alice".equals(a.principalId()))
                .findFirst().orElseThrow();
        assertTrue(aliceAce.permissions().contains("cmis:read"));
        assertTrue(aliceAce.permissions().contains("cmis:write"));

        CmisModels.AceEntry bobAce = response.aces().stream()
                .filter(a -> "bob".equals(a.principalId()))
                .findFirst().orElseThrow();
        assertEquals(List.of("cmis:read"), bobAce.permissions());
    }

    @Test
    @DisplayName("READ permission maps to cmis:read")
    void readPermissionMapsToCmisRead() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of(
                buildPermission(folder, "alice", PermissionType.READ, false)
        ));

        CmisModels.AclResponse response = cmisAclService.getAcl(folder.getId().toString());

        assertEquals(1, response.aces().size());
        assertEquals(List.of("cmis:read"), response.aces().get(0).permissions());
    }

    @Test
    @DisplayName("WRITE permission maps to cmis:write")
    void writePermissionMapsToCmisWrite() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of(
                buildPermission(folder, "alice", PermissionType.WRITE, false)
        ));

        CmisModels.AclResponse response = cmisAclService.getAcl(folder.getId().toString());

        assertEquals(1, response.aces().size());
        assertEquals(List.of("cmis:write"), response.aces().get(0).permissions());
    }

    @Test
    @DisplayName("DELETE permission maps to cmis:all")
    void deletePermissionMapsToCmisAll() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of(
                buildPermission(folder, "alice", PermissionType.DELETE, false)
        ));

        CmisModels.AclResponse response = cmisAclService.getAcl(folder.getId().toString());

        assertEquals(1, response.aces().size());
        assertEquals(List.of("cmis:all"), response.aces().get(0).permissions());
    }

    @Test
    @DisplayName("Inherited permissions are marked as isDirect=false")
    void inheritedPermissionsMarkedAsNotDirect() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        Permission inherited = buildPermission(folder, "alice", PermissionType.READ, false);
        inherited.setInherited(true);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of(inherited));

        CmisModels.AclResponse response = cmisAclService.getAcl(folder.getId().toString());

        assertEquals(1, response.aces().size());
        assertFalse(response.aces().get(0).isDirect());
    }

    @Test
    @DisplayName("Direct permissions are marked as isDirect=true")
    void directPermissionsMarkedAsDirect() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        Permission direct = buildPermission(folder, "alice", PermissionType.READ, false);
        direct.setInherited(false);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of(direct));

        CmisModels.AclResponse response = cmisAclService.getAcl(folder.getId().toString());

        assertEquals(1, response.aces().size());
        assertTrue(response.aces().get(0).isDirect());
    }

    @Test
    @DisplayName("applyAcl grants mapped Athena permissions for added CMIS ACEs")
    void applyAclGrantsPermissions() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of());

        CmisModels.AceEntry addAce = new CmisModels.AceEntry("alice", List.of("cmis:read"), true);
        cmisAclService.applyAcl(folder.getId().toString(), List.of(addAce), null);

        verify(securityService).setPermission(folder, "alice", AuthorityType.USER, PermissionType.READ, true);
    }

    @Test
    @DisplayName("applyAcl preserves group authority types")
    void applyAclPreservesGroupAuthorityTypes() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of());
        when(groupRepository.findByName("editors")).thenReturn(java.util.Optional.of(new com.ecm.core.entity.Group()));

        CmisModels.AceEntry addAce = new CmisModels.AceEntry("editors", List.of("cmis:write"), true);
        cmisAclService.applyAcl(folder.getId().toString(), List.of(addAce), null);

        verify(securityService).setPermission(folder, "editors", AuthorityType.GROUP, PermissionType.WRITE, true);
        verify(securityService).setPermission(folder, "editors", AuthorityType.GROUP, PermissionType.CREATE_CHILDREN, true);
    }

    @Test
    @DisplayName("applyAcl removes mapped Athena permissions for removed CMIS ACEs")
    void applyAclRemovesPermissions() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of());

        CmisModels.AceEntry removeAce = new CmisModels.AceEntry("bob", List.of("cmis:write"), true);
        cmisAclService.applyAcl(folder.getId().toString(), null, List.of(removeAce));

        verify(securityService).removePermission(folder, "bob", PermissionType.WRITE);
        verify(securityService).removePermission(folder, "bob", PermissionType.CREATE_CHILDREN);
    }

    @Test
    @DisplayName("cmis:all expands to the full Athena admin permission set")
    void applyAclExpandsCmisAllToFullAdminSet() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of());

        CmisModels.AceEntry addAce = new CmisModels.AceEntry("alice", List.of("cmis:all"), true);
        cmisAclService.applyAcl(folder.getId().toString(), List.of(addAce), null);

        verify(securityService).setPermission(folder, "alice", AuthorityType.USER, PermissionType.DELETE, true);
        verify(securityService).setPermission(folder, "alice", AuthorityType.USER, PermissionType.DELETE_CHILDREN, true);
        verify(securityService).setPermission(folder, "alice", AuthorityType.USER, PermissionType.CHANGE_PERMISSIONS, true);
        verify(securityService).setPermission(folder, "alice", AuthorityType.USER, PermissionType.TAKE_OWNERSHIP, true);
        verify(securityService).setPermission(folder, "alice", AuthorityType.USER, PermissionType.EXECUTE, true);
        verify(securityService).setPermission(folder, "alice", AuthorityType.USER, PermissionType.APPROVE, true);
        verify(securityService).setPermission(folder, "alice", AuthorityType.USER, PermissionType.REJECT, true);
    }

    @Test
    @DisplayName("Version-specific objectId resolves against the live node ACL")
    void versionSpecificObjectIdResolvesAgainstLiveNodeAcl() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of(
            buildPermission(folder, "alice", PermissionType.READ, false)
        ));

        CmisModels.AclResponse response = cmisAclService.getAcl(folder.getId() + ";v2.0");

        assertEquals(folder.getId() + ";v2.0", response.objectId());
        assertEquals(List.of("cmis:read"), response.aces().get(0).permissions());
    }

    @Test
    @DisplayName("Denied permissions are excluded from ACL response")
    void deniedPermissionsExcluded() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        Permission denied = buildPermission(folder, "alice", PermissionType.READ, false);
        denied.setAllowed(false);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of(denied));

        CmisModels.AclResponse response = cmisAclService.getAcl(folder.getId().toString());

        assertTrue(response.aces().isEmpty());
    }

    @Test
    @DisplayName("Same principal with both direct and inherited permissions produces separate ACE entries")
    void sameAuthorityDirectAndInheritedProduceSeparateEntries() {
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        Permission direct = buildPermission(folder, "alice", PermissionType.READ, false);
        direct.setInherited(false);
        Permission inherited = buildPermission(folder, "alice", PermissionType.WRITE, false);
        inherited.setInherited(true);
        when(securityService.getNodePermissions(folder)).thenReturn(List.of(direct, inherited));

        CmisModels.AclResponse response = cmisAclService.getAcl(folder.getId().toString());

        assertEquals(2, response.aces().size());
        CmisModels.AceEntry directAce = response.aces().stream()
                .filter(CmisModels.AceEntry::isDirect).findFirst().orElseThrow();
        CmisModels.AceEntry inheritedAce = response.aces().stream()
                .filter(a -> !a.isDirect()).findFirst().orElseThrow();
        assertTrue(directAce.permissions().contains("cmis:read"));
        assertTrue(inheritedAce.permissions().contains("cmis:write"));
    }

    private Permission buildPermission(Node node, String authority,
                                       PermissionType permissionType, boolean inherited) {
        Permission permission = new Permission();
        permission.setNode(node);
        permission.setAuthority(authority);
        permission.setAuthorityType(AuthorityType.USER);
        permission.setPermission(permissionType);
        permission.setAllowed(true);
        permission.setInherited(inherited);
        return permission;
    }
}
