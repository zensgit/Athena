package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Tenant;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TenantRepository;
import com.ecm.core.service.TenantContextResolverService.TargetFolderTenantException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantContextResolverServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private TenantRepository tenantRepository;

    @InjectMocks private TenantContextResolverService service;

    @Test
    void resolvesTenantWhenTargetFolderIsUnderATenantRoot() {
        UUID rootId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Node root = node(rootId, null);
        Node folder = node(folderId, root);

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        // folder itself is not a tenant root; its parent (rootId) is.
        when(tenantRepository.findByRootNodeIdAndDeletedFalse(folderId)).thenReturn(Optional.empty());
        when(nodeRepository.findByIdAndDeletedFalse(rootId)).thenReturn(Optional.of(root));
        when(tenantRepository.findByRootNodeIdAndDeletedFalse(rootId))
            .thenReturn(Optional.of(tenant("acme", rootId, true)));

        TenantContextResolverService.ResolvedTenant resolved = service.resolveTenantForTargetFolder(folderId);

        assertEquals("acme", resolved.tenantDomain());
        assertEquals(rootId, resolved.rootNodeId());
    }

    @Test
    void rejectsWithNotFoundWhenTargetFolderMissingOrDeleted() {
        UUID folderId = UUID.randomUUID();
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.empty());

        TargetFolderTenantException ex = assertThrows(TargetFolderTenantException.class,
            () -> service.resolveTenantForTargetFolder(folderId));
        assertEquals(TargetFolderTenantException.Reason.NOT_FOUND, ex.getReason());
    }

    @Test
    void rejectsWithNotUnderTenantWhenNoEnabledTenantRootInChain() {
        UUID rootId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Node root = node(rootId, null);
        Node folder = node(folderId, root);

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(nodeRepository.findByIdAndDeletedFalse(rootId)).thenReturn(Optional.of(root));
        // no ancestor id maps to a tenant root
        when(tenantRepository.findByRootNodeIdAndDeletedFalse(any())).thenReturn(Optional.empty());

        TargetFolderTenantException ex = assertThrows(TargetFolderTenantException.class,
            () -> service.resolveTenantForTargetFolder(folderId));
        assertEquals(TargetFolderTenantException.Reason.NOT_UNDER_TENANT, ex.getReason());
    }

    @Test
    void skipsDisabledTenantRootAndKeepsWalking() {
        UUID rootId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Node root = node(rootId, null);
        Node folder = node(folderId, root);

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(nodeRepository.findByIdAndDeletedFalse(rootId)).thenReturn(Optional.of(root));
        // folder id maps to a DISABLED tenant root → must not be used; keep walking → none found.
        when(tenantRepository.findByRootNodeIdAndDeletedFalse(folderId))
            .thenReturn(Optional.of(tenant("disabled-tenant", folderId, false)));
        lenient().when(tenantRepository.findByRootNodeIdAndDeletedFalse(rootId)).thenReturn(Optional.empty());

        TargetFolderTenantException ex = assertThrows(TargetFolderTenantException.class,
            () -> service.resolveTenantForTargetFolder(folderId));
        assertEquals(TargetFolderTenantException.Reason.NOT_UNDER_TENANT, ex.getReason());
    }

    private Node node(UUID id, Node parent) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setParent(parent);
        return folder;
    }

    private Tenant tenant(String domain, UUID rootNodeId, boolean enabled) {
        Tenant tenant = new Tenant();
        tenant.setTenantDomain(domain);
        tenant.setTenantName(domain);
        tenant.setRootNodeId(rootNodeId);
        tenant.setEnabled(enabled);
        return tenant;
    }
}
