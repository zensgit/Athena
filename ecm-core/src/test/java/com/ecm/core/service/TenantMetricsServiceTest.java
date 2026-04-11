package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Tenant;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantMetricsServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantQuotaService tenantQuotaService;
    @Mock private NodeRepository nodeRepository;
    @Mock private DocumentRepository documentRepository;

    private TenantMetricsService service;

    @BeforeEach
    void setUp() {
        service = new TenantMetricsService(tenantRepository, tenantQuotaService, nodeRepository, documentRepository);
    }

    @Test
    @DisplayName("Returns correct storage metrics from TenantQuotaService")
    void returnsCorrectStorageMetrics() {
        UUID rootId = UUID.randomUUID();
        Tenant tenant = tenant("acme", "Acme Corp", true, 10_000_000L, rootId);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));
        when(tenantQuotaService.calculateUsedBytes(tenant)).thenReturn(3_500_000L);
        when(nodeRepository.findById(rootId)).thenReturn(Optional.of(folderWithPath(rootId, "/acme workspace")));
        when(nodeRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(20L);
        when(documentRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(15L);

        TenantMetricsService.TenantMetrics metrics = service.getMetrics("acme");

        assertEquals("acme", metrics.tenantDomain());
        assertEquals("Acme Corp", metrics.tenantName());
        assertTrue(metrics.enabled());
        assertEquals(3_500_000L, metrics.storageUsedBytes());
        assertEquals(10_000_000L, metrics.quotaBytes());
    }

    @Test
    @DisplayName("Returns node, document, and folder counts")
    void returnsNodeDocumentFolderCounts() {
        UUID rootId = UUID.randomUUID();
        Tenant tenant = tenant("acme", "Acme Corp", true, null, rootId);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));
        when(tenantQuotaService.calculateUsedBytes(tenant)).thenReturn(1_000L);
        when(nodeRepository.findById(rootId)).thenReturn(Optional.of(folderWithPath(rootId, "/acme workspace")));
        when(nodeRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(25L);
        when(documentRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(18L);

        TenantMetricsService.TenantMetrics metrics = service.getMetrics("acme");

        assertEquals(25L, metrics.nodeCount());
        assertEquals(18L, metrics.documentCount());
        assertEquals(7L, metrics.folderCount());
    }

    @Test
    @DisplayName("Available bytes calculated correctly when quota is set")
    void availableBytesCalculatedWithQuota() {
        UUID rootId = UUID.randomUUID();
        Tenant tenant = tenant("acme", "Acme Corp", true, 10_000_000L, rootId);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));
        when(tenantQuotaService.calculateUsedBytes(tenant)).thenReturn(3_000_000L);
        when(nodeRepository.findById(rootId)).thenReturn(Optional.of(folderWithPath(rootId, "/acme workspace")));
        when(nodeRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(0L);
        when(documentRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(0L);

        TenantMetricsService.TenantMetrics metrics = service.getMetrics("acme");

        assertEquals(10_000_000L, metrics.quotaBytes());
        assertEquals(7_000_000L, metrics.storageAvailableBytes());
    }

    @Test
    @DisplayName("Available bytes clamps to zero when tenant is over quota")
    void availableBytesClampedWhenOverQuota() {
        UUID rootId = UUID.randomUUID();
        Tenant tenant = tenant("acme", "Acme Corp", true, 1_000L, rootId);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));
        when(tenantQuotaService.calculateUsedBytes(tenant)).thenReturn(1_500L);
        when(nodeRepository.findById(rootId)).thenReturn(Optional.of(folderWithPath(rootId, "/acme workspace")));
        when(nodeRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(0L);
        when(documentRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(0L);

        TenantMetricsService.TenantMetrics metrics = service.getMetrics("acme");

        assertEquals(0L, metrics.storageAvailableBytes());
    }

    @Test
    @DisplayName("Available bytes is null when no quota configured")
    void availableBytesNullWithoutQuota() {
        UUID rootId = UUID.randomUUID();
        Tenant tenant = tenant("acme", "Acme Corp", true, null, rootId);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));
        when(tenantQuotaService.calculateUsedBytes(tenant)).thenReturn(5_000L);
        when(nodeRepository.findById(rootId)).thenReturn(Optional.of(folderWithPath(rootId, "/acme workspace")));
        when(nodeRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(0L);
        when(documentRepository.countByDeletedFalseAndPathLike("/acme workspace/%")).thenReturn(0L);

        TenantMetricsService.TenantMetrics metrics = service.getMetrics("acme");

        assertNull(metrics.quotaBytes());
        assertNull(metrics.storageAvailableBytes());
    }

    @Test
    @DisplayName("Throws NoSuchElementException for unknown tenant")
    void throwsForUnknownTenant() {
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("unknown"))
            .thenReturn(Optional.empty());

        NoSuchElementException ex = assertThrows(
            NoSuchElementException.class,
            () -> service.getMetrics("unknown")
        );

        assertTrue(ex.getMessage().contains("unknown"));
    }

    @Test
    @DisplayName("Counts are zero when tenant has no root node")
    void countsZeroWhenNoRootNode() {
        Tenant tenant = tenant("acme", "Acme Corp", true, 5_000L, null);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));
        when(tenantQuotaService.calculateUsedBytes(tenant)).thenReturn(0L);

        TenantMetricsService.TenantMetrics metrics = service.getMetrics("acme");

        assertEquals(0L, metrics.nodeCount());
        assertEquals(0L, metrics.documentCount());
        assertEquals(0L, metrics.folderCount());
        assertEquals(0L, metrics.storageUsedBytes());
    }

    private Tenant tenant(String domain, String name, boolean enabled, Long quotaBytes, UUID rootNodeId) {
        Tenant tenant = new Tenant();
        tenant.setTenantDomain(domain);
        tenant.setTenantName(name);
        tenant.setEnabled(enabled);
        tenant.setQuotaBytes(quotaBytes);
        tenant.setRootNodeId(rootNodeId);
        return tenant;
    }

    private Node folderWithPath(UUID id, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(path.substring(path.lastIndexOf('/') + 1));
        folder.setPath(path);
        return folder;
    }
}
