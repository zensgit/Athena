package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Tenant;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TenantRepository;
import com.ecm.core.service.TenantContextResolverService.TenantResolution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The resolver returns a {@link TenantResolution} and never throws — see the service javadoc for why
 * (throwing across its {@code @Transactional(readOnly)} proxy would poison a caller's transaction).
 * The load-bearing distinction is UNRESOLVED (tenants exist, folder under none → caller rejects) vs
 * NO_TENANT_SYSTEM (no tenant exists at all → caller writes untenanted), keyed off
 * {@code existsByDeletedFalseAndEnabledTrue}.
 */
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

        TenantResolution resolved = service.resolveTenantForTargetFolder(folderId);

        // RESOLVED short-circuits before the existsBy check, so that stub is intentionally absent.
        assertTrue(resolved.isResolved());
        assertEquals("acme", resolved.tenantDomain());
        assertEquals(rootId, resolved.rootNodeId());
    }

    @Test
    void rejectsAsUnresolvedWhenFolderMissingAndTenantsExist() {
        UUID folderId = UUID.randomUUID();
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.empty());
        when(tenantRepository.existsByDeletedFalseAndEnabledTrueAndRootNodeIdNotNull()).thenReturn(true);

        TenantResolution res = service.resolveTenantForTargetFolder(folderId);

        // tenants exist but the folder is missing → caller must reject (no untenanted write).
        assertTrue(res.isReject());
    }

    @Test
    void noTenantSystemWhenFolderMissingAndNoTenants() {
        UUID folderId = UUID.randomUUID();
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.empty());
        when(tenantRepository.existsByDeletedFalseAndEnabledTrueAndRootNodeIdNotNull()).thenReturn(false);

        TenantResolution res = service.resolveTenantForTargetFolder(folderId);

        // no tenant configured anywhere → legacy single-tenant deployment; caller writes untenanted.
        assertTrue(res.isNoTenantSystem());
    }

    @Test
    void rejectsAsUnresolvedWhenFolderIdIsNullAndTenantsExist() {
        // A null/missing folder id must surface as a reject in a multi-tenant system rather than an
        // untenanted root write. No node/parent stubbing — a null folder id skips the walk entirely.
        when(tenantRepository.existsByDeletedFalseAndEnabledTrueAndRootNodeIdNotNull()).thenReturn(true);

        TenantResolution res = service.resolveTenantForTargetFolder(null);

        assertTrue(res.isReject());
    }

    @Test
    void noTenantSystemWhenFolderIdIsNullAndNoTenants() {
        when(tenantRepository.existsByDeletedFalseAndEnabledTrueAndRootNodeIdNotNull()).thenReturn(false);

        TenantResolution res = service.resolveTenantForTargetFolder(null);

        assertTrue(res.isNoTenantSystem());
    }

    @Test
    void rejectsAsUnresolvedWhenNoEnabledTenantRootInChainButTenantsExist() {
        UUID rootId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Node root = node(rootId, null);
        Node folder = node(folderId, root);

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(nodeRepository.findByIdAndDeletedFalse(rootId)).thenReturn(Optional.of(root));
        // no ancestor id maps to a tenant root, but tenants exist elsewhere → reject.
        when(tenantRepository.findByRootNodeIdAndDeletedFalse(any())).thenReturn(Optional.empty());
        when(tenantRepository.existsByDeletedFalseAndEnabledTrueAndRootNodeIdNotNull()).thenReturn(true);

        TenantResolution res = service.resolveTenantForTargetFolder(folderId);

        assertTrue(res.isReject());
    }

    @Test
    void skipsDisabledTenantRootAndRejectsWhenTenantsExist() {
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
        when(tenantRepository.existsByDeletedFalseAndEnabledTrueAndRootNodeIdNotNull()).thenReturn(true);

        TenantResolution res = service.resolveTenantForTargetFolder(folderId);

        // a disabled tenant root is not a valid scope; with other tenants present this is a reject.
        assertTrue(res.isReject());
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
