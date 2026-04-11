package com.ecm.core.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Tenant;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantQuotaServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private NodeRepository nodeRepository;

    private TenantQuotaService service;

    @BeforeEach
    void setUp() {
        service = new TenantQuotaService(tenantRepository, documentRepository, nodeRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("assertQuotaAvailable passes when no quota configured")
    void noQuotaConfiguredPasses() {
        TenantContext.setCurrentTenantDomain("acme");
        Tenant tenant = tenant("acme", null);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));

        assertDoesNotThrow(() -> service.assertQuotaAvailable(1_000_000));
    }

    @Test
    @DisplayName("assertQuotaAvailable passes when within quota")
    void withinQuotaPasses() {
        TenantContext.setCurrentTenantDomain("acme");
        UUID rootId = UUID.randomUUID();
        Tenant tenant = tenant("acme", 10_000_000L);
        tenant.setRootNodeId(rootId);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));
        when(nodeRepository.findById(rootId)).thenReturn(Optional.of(folderWithPath(rootId, "/acme workspace")));
        when(documentRepository.sumFileSizeByPathPrefix("/acme workspace/%")).thenReturn(5_000_000L);

        assertDoesNotThrow(() -> service.assertQuotaAvailable(4_000_000));
    }

    @Test
    @DisplayName("assertQuotaAvailable throws when quota exceeded")
    void quotaExceededThrows() {
        TenantContext.setCurrentTenantDomain("acme");
        UUID rootId = UUID.randomUUID();
        Tenant tenant = tenant("acme", 10_000_000L);
        tenant.setRootNodeId(rootId);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));
        when(nodeRepository.findById(rootId)).thenReturn(Optional.of(folderWithPath(rootId, "/acme workspace")));
        when(documentRepository.sumFileSizeByPathPrefix("/acme workspace/%")).thenReturn(8_000_000L);

        TenantQuotaService.QuotaExceededException ex = assertThrows(
            TenantQuotaService.QuotaExceededException.class,
            () -> service.assertQuotaAvailable(5_000_000)
        );

        assertEquals("acme", ex.getTenantDomain());
        assertEquals(10_000_000L, ex.getQuotaBytes());
        assertEquals(8_000_000L, ex.getUsedBytes());
        assertEquals(5_000_000L, ex.getRequestedBytes());
        assertTrue(ex instanceof IllegalArgumentException);
        assertTrue(ex.getMessage().contains("available=2000000 bytes"));
    }

    @Test
    @DisplayName("hasAvailableQuota returns false when quota would be exceeded")
    void hasAvailableQuotaReturnsFalseWhenExceeded() {
        TenantContext.setCurrentTenantDomain("acme");
        UUID rootId = UUID.randomUUID();
        Tenant tenant = tenant("acme", 10_000_000L);
        tenant.setRootNodeId(rootId);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant));
        when(nodeRepository.findById(rootId)).thenReturn(Optional.of(folderWithPath(rootId, "/acme workspace")));
        when(documentRepository.sumFileSizeByPathPrefix("/acme workspace/%")).thenReturn(9_500_000L);

        assertFalse(service.hasAvailableQuota(1_000_000));
        assertTrue(service.hasAvailableQuota(400_000));
    }

    @Test
    @DisplayName("assertQuotaAvailable passes when no tenant context set")
    void noTenantContextPasses() {
        // No TenantContext.set — domain is null
        assertDoesNotThrow(() -> service.assertQuotaAvailable(999_999_999));
    }

    private Tenant tenant(String domain, Long quotaBytes) {
        Tenant tenant = new Tenant();
        tenant.setTenantDomain(domain);
        tenant.setTenantName(domain);
        tenant.setEnabled(true);
        tenant.setQuotaBytes(quotaBytes);
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
